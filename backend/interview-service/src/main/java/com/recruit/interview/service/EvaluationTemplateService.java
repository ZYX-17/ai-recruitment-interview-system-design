package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.EvaluationTemplateCreateForm;
import com.recruit.interview.dto.EvaluationTemplatePageQuery;
import com.recruit.interview.dto.EvaluationTemplateUpdateForm;
import com.recruit.interview.dto.EvaluationTemplateView;
import com.recruit.interview.dto.ProcessTemplateCreateForm;
import com.recruit.interview.dto.ProcessTemplatePageQuery;
import com.recruit.interview.dto.ProcessTemplateUpdateForm;
import com.recruit.interview.dto.ProcessTemplateView;
import com.recruit.interview.dto.ScoreDim;
import com.recruit.interview.dto.StageDef;
import com.recruit.interview.entity.EvaluationDimension;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.mapper.EvaluationDimensionMapper;
import com.recruit.interview.mapper.EvaluationMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import com.recruit.interview.support.IdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 评价模板（P6-1）。
 *
 * <p>与流程模板同源（docs/4 §7.6）：{@code job_stage_template} 提供版本，
 * {@code evaluation_dimension} 提供维度明细。这里只聚焦评分维度：
 * 创建/修改仍走 {@link ProcessTemplateService} 的版本化逻辑，避免两套实现漂移；
 * 读取维度以 {@code evaluation_dimension} 为准。</p>
 */
@Service
@RequiredArgsConstructor
public class EvaluationTemplateService {

    private static final TypeReference<List<StageDef>> STAGE_FLOW_TYPE = new TypeReference<>() {
    };

    private final ProcessTemplateService processTemplateService;
    private final JobStageTemplateMapper templateMapper;
    private final EvaluationDimensionMapper dimensionMapper;
    private final EvaluationMapper evaluationMapper;
    private final ObjectMapper objectMapper;

    /**
     * 评价模板分页。
     *
     * @param query 分页与过滤条件
     * @return 分页模板
     */
    public PageResult<EvaluationTemplateView> page(EvaluationTemplatePageQuery query) {
        ProcessTemplatePageQuery delegate = new ProcessTemplatePageQuery();
        delegate.setPageNum(query.getPageNum());
        delegate.setPageSize(query.getPageSize());
        delegate.setJobId(query.getJobId());
        delegate.setStatus(query.getStatus());
        PageResult<ProcessTemplateView> result = processTemplateService.page(delegate);
        List<EvaluationTemplateView> views = new ArrayList<>(result.getList().size());
        for (ProcessTemplateView view : result.getList()) {
            views.add(toView(view));
        }
        return PageResult.of(views, result.getTotal(), result.getPageNum(), result.getPageSize());
    }

    /**
     * 评价模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    public EvaluationTemplateView detail(long id) {
        return toView(processTemplateService.detail(id));
    }

    /**
     * 新增评价模板：沿用岗位当前流程阶段，只替换评分维度。
     *
     * @param form 新增入参
     * @param key  Idempotency-Key，可为空
     * @return 新模板
     */
    @Transactional
    public EvaluationTemplateView create(EvaluationTemplateCreateForm form, String key) {
        long jobId = IdSupport.requireLong(form.getJobId(), "jobId");
        JobStageTemplate active = templateMapper.selectActiveByJobId(jobId);
        if (active == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位未配置流程模板，无法创建评价模板");
        }
        ProcessTemplateCreateForm delegate = new ProcessTemplateCreateForm();
        delegate.setJobId(String.valueOf(jobId));
        delegate.setStageFlow(form.getStageFlow() == null || form.getStageFlow().isEmpty()
                ? readStageFlow(active.getStageFlow()) : form.getStageFlow());
        delegate.setScoreDims(form.getScoreDims());
        return toView(processTemplateService.create(delegate, key));
    }

    /**
     * 修改评价模板：生成新版本，旧版本只读。
     *
     * @param id   模板 ID
     * @param form 修改入参
     * @return 新版本
     */
    @Transactional
    public EvaluationTemplateView update(long id, EvaluationTemplateUpdateForm form) {
        ProcessTemplateView current = processTemplateService.detail(id);
        ProcessTemplateUpdateForm delegate = new ProcessTemplateUpdateForm();
        delegate.setStageFlow(form.getStageFlow() == null || form.getStageFlow().isEmpty()
                ? current.stageFlow() : form.getStageFlow());
        delegate.setScoreDims(form.getScoreDims());
        delegate.setVersion(form.getVersion());
        return toView(processTemplateService.update(id, delegate));
    }

    /**
     * 删除评价模板：已被历史评价引用时拒绝。
     *
     * @param id 模板 ID
     */
    @Transactional
    public void delete(long id) {
        ProcessTemplateView current = processTemplateService.detail(id);
        long jobId = IdSupport.requireLong(current.jobId(), "jobId");
        if (evaluationMapper.countByJobAndTemplateVer(jobId, current.templateVer()) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "评价模板已被历史评价引用，不能删除");
        }
        processTemplateService.delete(id);
    }

    private EvaluationTemplateView toView(ProcessTemplateView view) {
        long templateId = IdSupport.requireLong(view.id(), "id");
        List<EvaluationDimension> rows = dimensionMapper.selectByTemplateId(templateId);
        List<ScoreDim> dimensions = new ArrayList<>(rows.size());
        for (EvaluationDimension row : rows) {
            ScoreDim dim = new ScoreDim();
            dim.setDimCode(row.getDimCode());
            dim.setDimName(row.getDimName());
            dim.setWeight(row.getWeight());
            dim.setFullScore(row.getFullScore());
            dim.setVetoThreshold(row.getVetoThreshold());
            dimensions.add(dim);
        }
        return new EvaluationTemplateView(view.id(), view.jobId(), view.templateVer(),
                view.stageFlow(), dimensions, view.status(), view.createdAt(), view.version());
    }

    private List<StageDef> readStageFlow(String json) {
        try {
            List<StageDef> flow = objectMapper.readValue(json, STAGE_FLOW_TYPE);
            return flow == null ? List.of() : flow;
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "流程模板配置损坏", e);
        }
    }
}
