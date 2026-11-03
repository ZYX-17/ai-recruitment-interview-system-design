package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.ProcessTemplateCreateForm;
import com.recruit.interview.dto.ProcessTemplatePageQuery;
import com.recruit.interview.dto.ProcessTemplateUpdateForm;
import com.recruit.interview.dto.ProcessTemplateView;
import com.recruit.interview.dto.ScoreDim;
import com.recruit.interview.dto.StageDef;
import com.recruit.interview.entity.EvaluationDimension;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.EvaluationDimensionMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import com.recruit.interview.support.IdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 岗位流程模板（P4-3）。
 *
 * <p>模板按 {@code job_id + template_ver} 版本化：修改生成新版本，旧版本只读；
 * 同一岗位只保留一个 ACTIVE。权重合计必须为 1.0（允许 0.99~1.01），
 * 评分维度同时写入 {@code evaluation_dimension} 供评价模块渲染。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcessTemplateService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final Set<String> TEMPLATE_STATUSES = Set.of(STATUS_ACTIVE, STATUS_INACTIVE);
    private static final Set<String> STAGE_MODES = Set.of("INTERNAL", "ASYNC", "ONLINE", "OFFER");
    private static final BigDecimal MIN_WEIGHT_SUM = new BigDecimal("0.99");
    private static final BigDecimal MAX_WEIGHT_SUM = new BigDecimal("1.01");

    private static final TypeReference<List<StageDef>> STAGE_FLOW_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ScoreDim>> SCORE_DIM_TYPE = new TypeReference<>() {
    };

    private final JobMapper jobMapper;
    private final JobStageTemplateMapper templateMapper;
    private final EvaluationDimensionMapper dimensionMapper;
    private final ApplicationMapper applicationMapper;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;
    private final IdempotencyService idempotencyService;

    /**
     * 模板分页。
     *
     * @param query 分页与过滤条件
     * @return 分页模板
     */
    public PageResult<ProcessTemplateView> page(ProcessTemplatePageQuery query) {
        DataScopeService.DataScope scope = dataScopeService.current();
        Long jobId = IdSupport.optionalLong(query.getJobId(), "jobId");
        String status = normalizeStatus(query.getStatus());
        String sqlScope = scope.sqlScope();

        long total = templateMapper.countTemplatePage(jobId, status, sqlScope,
                scope.deptIds(), scope.userId());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<JobStageTemplate> rows = templateMapper.selectTemplatePage(jobId, status, sqlScope,
                scope.deptIds(), scope.userId(), query.getPageSize(), offset);
        List<ProcessTemplateView> views = new ArrayList<>(rows.size());
        for (JobStageTemplate row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    public ProcessTemplateView detail(long id) {
        JobStageTemplate template = loadVisible(id);
        return toView(template);
    }

    /**
     * 新增模板：版本号自动取 max+1，并激活为本岗位唯一 ACTIVE。
     *
     * @param form 新增入参
     * @param key  Idempotency-Key，可为空
     * @return 新模板
     */
    @Transactional
    public ProcessTemplateView create(ProcessTemplateCreateForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute("interview.process.create", key,
                    ProcessTemplateView.class, () -> doCreate(form));
        }
        return doCreate(form);
    }

    private ProcessTemplateView doCreate(ProcessTemplateCreateForm form) {
        long jobId = IdSupport.requireLong(form.getJobId(), "jobId");
        Job job = lockWritableJob(jobId);
        List<StageDef> stageFlow = normalizeStageFlow(form.getStageFlow());
        List<ScoreDim> scoreDims = normalizeScoreDims(form.getScoreDims());
        return insertVersion(job, stageFlow, scoreDims, currentUserId());
    }

    /**
     * 修改模板：校验版本后生成 {@code templateVer+1} 的新版本，旧版本保留只读。
     *
     * @param id   模板 ID
     * @param form 修改入参
     * @return 新版本模板
     */
    @Transactional
    public ProcessTemplateView update(long id, ProcessTemplateUpdateForm form) {
        JobStageTemplate current = loadVisible(id);
        Job job = lockWritableJob(current.getJobId());
        // 锁住岗位后重新读取，保证并发修改只有一个能通过版本校验
        current = templateMapper.selectById(id);
        if (current == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "流程模板不存在");
        }
        if (!STATUS_ACTIVE.equals(current.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "历史版本只读，请基于当前生效版本修改");
        }
        if (!current.getVersion().equals(form.getVersion())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        List<StageDef> stageFlow = normalizeStageFlow(form.getStageFlow());
        List<ScoreDim> scoreDims = normalizeScoreDims(form.getScoreDims());
        return insertVersion(job, stageFlow, scoreDims, currentUserId());
    }

    /**
     * 删除模板：岗位已有投递时拒绝；删除 ACTIVE 后回退到最新剩余版本。
     *
     * @param id 模板 ID
     */
    @Transactional
    public void delete(long id) {
        JobStageTemplate template = loadVisible(id);
        lockWritableJob(template.getJobId());
        if (applicationMapper.countByJob(template.getJobId()) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位已有投递，流程模板不可删除");
        }
        boolean wasActive = STATUS_ACTIVE.equals(template.getStatus());
        dimensionMapper.deleteByTemplateId(id);
        templateMapper.deleteById(id);
        if (wasActive) {
            JobStageTemplate latest = templateMapper.selectLatestByJobId(template.getJobId());
            if (latest != null && !STATUS_ACTIVE.equals(latest.getStatus())) {
                JobStageTemplate activate = new JobStageTemplate();
                activate.setId(latest.getId());
                activate.setStatus(STATUS_ACTIVE);
                templateMapper.updateById(activate);
            }
        }
    }

    private ProcessTemplateView insertVersion(Job job, List<StageDef> stageFlow,
                                              List<ScoreDim> scoreDims, long operatorId) {
        int nextVersion = templateMapper.selectMaxVersion(job.getId()) + 1;
        templateMapper.deactivateActive(job.getId());

        JobStageTemplate template = new JobStageTemplate();
        template.setJobId(job.getId());
        template.setTemplateVer(nextVersion);
        template.setStageFlow(writeJson(stageFlow));
        template.setScoreDims(writeJson(scoreDims));
        template.setStatus(STATUS_ACTIVE);
        template.setCreatedBy(operatorId);
        template.setVersion(0);
        templateMapper.insert(template);

        int sort = 0;
        for (ScoreDim dim : scoreDims) {
            EvaluationDimension entity = new EvaluationDimension();
            entity.setTemplateId(template.getId());
            entity.setDimCode(dim.getDimCode());
            entity.setDimName(dim.getDimName());
            entity.setWeight(dim.getWeight());
            entity.setFullScore(dim.getFullScore());
            entity.setVetoThreshold(dim.getVetoThreshold());
            entity.setSort(sort++);
            dimensionMapper.insert(entity);
        }
        JobStageTemplate saved = templateMapper.selectById(template.getId());
        return toView(saved == null ? template : saved);
    }

    private Job lockWritableJob(long jobId) {
        Job job = jobMapper.selectByIdForUpdate(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        requireVisible(job);
        if (JobService.STATUS_CLOSED.equals(job.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "已关闭岗位不可维护流程模板");
        }
        return job;
    }

    private JobStageTemplate loadVisible(long id) {
        JobStageTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "流程模板不存在");
        }
        Job job = jobMapper.selectById(template.getJobId());
        if (job == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        requireVisible(job);
        return template;
    }

    private void requireVisible(Job job) {
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(job.getDeptId(), job.getOwnerHrId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该岗位的流程模板");
        }
    }

    private List<StageDef> normalizeStageFlow(List<StageDef> stageFlow) {
        if (stageFlow == null || stageFlow.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流程阶段不能为空");
        }
        Set<String> stageNos = new HashSet<>();
        List<StageDef> normalized = new ArrayList<>(stageFlow.size());
        for (StageDef stage : stageFlow) {
            String stageNo = stage.getStageNo().trim().toUpperCase(Locale.ROOT);
            if (!stageNos.add(stageNo)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "阶段编码重复: " + stageNo);
            }
            String mode = stage.getMode().trim().toUpperCase(Locale.ROOT);
            if (!STAGE_MODES.contains(mode)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "阶段模式非法: " + mode);
            }
            StageDef item = new StageDef();
            item.setStageNo(stageNo);
            item.setStageName(stage.getStageName().trim());
            item.setMode(mode);
            item.setRounds(stage.getRounds() == null ? 0 : stage.getRounds());
            normalized.add(item);
        }
        return normalized;
    }

    private List<ScoreDim> normalizeScoreDims(List<ScoreDim> scoreDims) {
        if (scoreDims == null || scoreDims.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评分维度不能为空");
        }
        Set<String> dimCodes = new HashSet<>();
        List<ScoreDim> normalized = new ArrayList<>(scoreDims.size());
        BigDecimal weightSum = BigDecimal.ZERO;
        for (ScoreDim dim : scoreDims) {
            String dimCode = dim.getDimCode().trim().toUpperCase(Locale.ROOT);
            if (!dimCodes.add(dimCode)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "评分维度编码重复: " + dimCode);
            }
            BigDecimal weight = dim.getWeight();
            if (weight == null || weight.signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "评分维度权重必须大于 0");
            }
            if (dim.getFullScore() == null || dim.getFullScore().signum() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "评分维度满分必须大于 0");
            }
            if (dim.getVetoThreshold() != null
                    && dim.getVetoThreshold().compareTo(dim.getFullScore()) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "否决阈值不能大于满分");
            }
            weightSum = weightSum.add(weight);
            ScoreDim item = new ScoreDim();
            item.setDimCode(dimCode);
            item.setDimName(dim.getDimName().trim());
            item.setWeight(weight);
            item.setFullScore(dim.getFullScore());
            item.setVetoThreshold(dim.getVetoThreshold());
            normalized.add(item);
        }
        if (weightSum.compareTo(MIN_WEIGHT_SUM) < 0 || weightSum.compareTo(MAX_WEIGHT_SUM) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评分维度权重合计必须为 1.0（允许 0.99~1.01）");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!TEMPLATE_STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "模板状态非法");
        }
        return status;
    }

    private ProcessTemplateView toView(JobStageTemplate template) {
        return new ProcessTemplateView(
                String.valueOf(template.getId()),
                String.valueOf(template.getJobId()),
                template.getTemplateVer(),
                readJson(template.getStageFlow(), STAGE_FLOW_TYPE),
                readJson(template.getScoreDims(), SCORE_DIM_TYPE),
                template.getStatus(),
                IdSupport.asString(template.getCreatedBy()),
                template.getCreatedAt() == null ? null
                        : template.getCreatedAt().toInstant(ZoneOffset.UTC),
                template.getVersion());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "流程模板序列化失败", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "流程模板配置损坏", e);
        }
    }

    private static long currentUserId() {
        return Long.parseLong(UserContext.requireUserId());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
