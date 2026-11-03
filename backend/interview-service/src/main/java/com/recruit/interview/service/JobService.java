package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.JobPublishedPayload;
import com.recruit.contracts.event.payload.JobUpdatedPayload;
import com.recruit.interview.dto.JobCreateForm;
import com.recruit.interview.dto.JobPageQuery;
import com.recruit.interview.dto.JobUpdateForm;
import com.recruit.interview.dto.JobView;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.EvaluationDimensionMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import com.recruit.interview.mapper.row.JobRow;
import com.recruit.interview.support.IdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 岗位管理（P4-1/P4-2/P4-7）。
 *
 * <p>关键口径：</p>
 * <ul>
 *   <li>{@code jobCode} 唯一且不可改；</li>
 *   <li>状态：DRAFT → PUBLISHED → PAUSED/CLOSED，CLOSED 不可再投递；</li>
 *   <li>发布/更新已发布岗位时，业务写库与 {@code local_message} 在同一事务；</li>
 *   <li>删除仅限 DRAFT 且无投递，否则要求走 CLOSED；</li>
 *   <li>列表/详情按数据范围过滤：DEPARTMENT 看部门子树，SELF/ASSIGNED 看负责人。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class JobService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_CLOSED = "CLOSED";

    private static final List<String> STATUSES = List.of(
            STATUS_DRAFT, STATUS_PUBLISHED, STATUS_PAUSED, STATUS_CLOSED);

    private final JobMapper jobMapper;
    private final JobStageTemplateMapper templateMapper;
    private final EvaluationDimensionMapper dimensionMapper;
    private final ApplicationMapper applicationMapper;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;
    private final IdempotencyService idempotencyService;

    /**
     * 岗位分页。
     *
     * @param query 分页与过滤条件
     * @return 分页岗位
     */
    public PageResult<JobView> page(JobPageQuery query) {
        DataScopeService.DataScope scope = dataScopeService.current();
        String status = trimToNull(query.getStatus());
        if (status != null) {
            status = requireStatus(status);
        }
        Long deptId = IdSupport.optionalLong(query.getDeptId(), "deptId");
        String keyword = trimToNull(query.getKeyword());
        String sqlScope = scope.sqlScope();

        long total = jobMapper.countJobPage(keyword, status, deptId, sqlScope,
                scope.deptIds(), scope.userId());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<JobRow> rows = jobMapper.selectJobPage(keyword, status, deptId, sqlScope,
                scope.deptIds(), scope.userId(), query.getPageSize(), offset);
        List<JobView> views = new ArrayList<>(rows.size());
        for (JobRow row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 岗位详情。
     *
     * @param id 岗位 ID
     * @return 岗位详情
     */
    public JobView detail(long id) {
        Job job = loadVisible(id);
        return toView(job, jobMapper.countApplications(id));
    }

    /**
     * 新增岗位。
     *
     * @param form 新增入参
     * @param key  Idempotency-Key，可为空
     * @return 新岗位
     */
    @Transactional
    public JobView create(JobCreateForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute("interview.job.create", key, JobView.class,
                    () -> doCreate(form));
        }
        return doCreate(form);
    }

    private JobView doCreate(JobCreateForm form) {
        String jobCode = form.getJobCode().trim();
        if (jobMapper.selectByJobCode(jobCode) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位编码已存在");
        }
        long operatorId = currentUserId();

        Job job = new Job();
        job.setJobCode(jobCode);
        job.setTitle(form.getTitle().trim());
        job.setDeptId(IdSupport.optionalLong(form.getDeptId(), "deptId"));
        job.setDeptName(trimToNull(form.getDeptName()));
        job.setJdText(form.getJdText());
        job.setRequirementsJson(writeJson(form.getRequirements()));
        job.setHeadcount(form.getHeadcount() == null ? 1 : form.getHeadcount());
        job.setSalaryRange(trimToNull(form.getSalaryRange()));
        job.setStatus(STATUS_DRAFT);
        Long ownerHrId = IdSupport.optionalLong(form.getOwnerHrId(), "ownerHrId");
        job.setOwnerHrId(ownerHrId == null ? operatorId : ownerHrId);
        job.setVersion(0);
        jobMapper.insert(job);
        Job saved = jobMapper.selectById(job.getId());
        return toView(saved == null ? job : saved, 0);
    }

    /**
     * 修改岗位。已发布/暂停岗位更新后发布 {@code job.updated}。
     *
     * @param id   岗位 ID
     * @param form 修改入参
     * @return 更新后的岗位
     */
    @Transactional
    public JobView update(long id, JobUpdateForm form) {
        Job current = lockVisible(id);
        if (STATUS_CLOSED.equals(current.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "已关闭岗位不可修改");
        }

        Job update = new Job();
        update.setId(current.getId());
        update.setVersion(form.getVersion());
        update.setTitle(hasText(form.getTitle()) ? form.getTitle().trim() : current.getTitle());
        update.setDeptId(form.getDeptId() == null
                ? current.getDeptId() : IdSupport.optionalLong(form.getDeptId(), "deptId"));
        update.setDeptName(form.getDeptName() == null
                ? current.getDeptName() : trimToNull(form.getDeptName()));
        update.setJdText(form.getJdText() == null ? current.getJdText() : form.getJdText());
        update.setRequirementsJson(form.getRequirements() == null
                ? current.getRequirementsJson() : writeJson(form.getRequirements()));
        update.setHeadcount(form.getHeadcount() == null ? current.getHeadcount() : form.getHeadcount());
        update.setSalaryRange(form.getSalaryRange() == null
                ? current.getSalaryRange() : trimToNull(form.getSalaryRange()));
        Long ownerHrId = IdSupport.optionalLong(form.getOwnerHrId(), "ownerHrId");
        update.setOwnerHrId(ownerHrId == null ? current.getOwnerHrId() : ownerHrId);

        if (jobMapper.updateWithVersion(update) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }

        List<String> changedFields = changedFields(current, update);
        int newVersion = form.getVersion() + 1;
        if (!STATUS_DRAFT.equals(current.getStatus()) && !changedFields.isEmpty()) {
            outboxPublisher.publish(EventTypes.JOB_UPDATED, String.valueOf(id), "job",
                    new JobUpdatedPayload(String.valueOf(id), newVersion, current.getStatus(), changedFields));
        }
        // 更新可能把负责人/部门改到当前用户范围之外，这里不再做可见性校验，避免已成功的更新被回滚
        Job updated = jobMapper.selectById(id);
        if (updated == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        return toView(updated, jobMapper.countApplications(id));
    }

    /**
     * 发布岗位：DRAFT/PAUSED → PUBLISHED；已发布时按幂等返回。
     *
     * @param id      岗位 ID
     * @param version 期望版本
     * @return 发布后的岗位
     */
    @Transactional
    public JobView publish(long id, int version) {
        Job current = lockVisible(id);
        if (STATUS_CLOSED.equals(current.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "已关闭岗位不可发布");
        }
        if (STATUS_PUBLISHED.equals(current.getStatus())) {
            requireVersion(current, version);
            return toView(current, jobMapper.countApplications(id));
        }
        requireVersion(current, version);

        LocalDateTime publishedAt = current.getPublishedAt() == null
                ? LocalDateTime.now(ZoneOffset.UTC)
                : current.getPublishedAt();
        if (jobMapper.updateStatus(id, STATUS_PUBLISHED, publishedAt, version) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }

        int newVersion = version + 1;
        outboxPublisher.publish(EventTypes.JOB_PUBLISHED, String.valueOf(id), "job",
                new JobPublishedPayload(String.valueOf(id), current.getJobCode(), current.getTitle(),
                        IdSupport.asString(current.getDeptId()), newVersion, STATUS_PUBLISHED,
                        publishedAt.toInstant(ZoneOffset.UTC)));
        return detail(id);
    }

    /**
     * 关闭岗位：PUBLISHED/PAUSED → CLOSED；已关闭时按幂等返回。
     *
     * @param id      岗位 ID
     * @param version 期望版本
     * @return 关闭后的岗位
     */
    @Transactional
    public JobView close(long id, int version) {
        Job current = lockVisible(id);
        if (STATUS_CLOSED.equals(current.getStatus())) {
            requireVersion(current, version);
            return toView(current, jobMapper.countApplications(id));
        }
        if (STATUS_DRAFT.equals(current.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "草稿岗位不可关闭，请直接删除");
        }
        requireVersion(current, version);
        if (jobMapper.updateStatus(id, STATUS_CLOSED, null, version) == 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }

        outboxPublisher.publish(EventTypes.JOB_UPDATED, String.valueOf(id), "job",
                new JobUpdatedPayload(String.valueOf(id), version + 1, STATUS_CLOSED, List.of("status")));
        return detail(id);
    }

    /**
     * 删除岗位：仅 DRAFT 且无投递。
     *
     * @param id 岗位 ID
     */
    @Transactional
    public void delete(long id) {
        Job current = lockVisible(id);
        if (!STATUS_DRAFT.equals(current.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅草稿岗位可删除，已发布岗位请使用关闭");
        }
        if (applicationMapper.countByJob(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位已有投递，不能删除");
        }
        for (JobStageTemplate template : templateMapper.selectByJobId(id)) {
            dimensionMapper.deleteByTemplateId(template.getId());
            templateMapper.deleteById(template.getId());
        }
        jobMapper.deleteById(id);
    }

    private Job loadVisible(long id) {
        Job job = jobMapper.selectById(id);
        if (job == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        requireVisible(job);
        return job;
    }

    private Job lockVisible(long id) {
        Job job = jobMapper.selectByIdForUpdate(id);
        if (job == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        requireVisible(job);
        return job;
    }

    private void requireVisible(Job job) {
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(job.getDeptId(), job.getOwnerHrId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该岗位");
        }
    }

    private static void requireVersion(Job job, int version) {
        int current = job.getVersion() == null ? 0 : job.getVersion();
        if (current != version) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
    }

    private static String requireStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位状态非法");
        }
        return status;
    }

    private List<String> changedFields(Job before, Job after) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(before.getTitle(), after.getTitle())) {
            fields.add("title");
        }
        if (!Objects.equals(before.getDeptId(), after.getDeptId())) {
            fields.add("deptId");
        }
        if (!Objects.equals(before.getDeptName(), after.getDeptName())) {
            fields.add("deptName");
        }
        if (!Objects.equals(before.getJdText(), after.getJdText())) {
            fields.add("jdText");
        }
        if (!Objects.equals(before.getRequirementsJson(), after.getRequirementsJson())) {
            fields.add("requirements");
        }
        if (!Objects.equals(before.getHeadcount(), after.getHeadcount())) {
            fields.add("headcount");
        }
        if (!Objects.equals(before.getSalaryRange(), after.getSalaryRange())) {
            fields.add("salaryRange");
        }
        if (!Objects.equals(before.getOwnerHrId(), after.getOwnerHrId())) {
            fields.add("ownerHrId");
        }
        return fields;
    }

    private JobView toView(Job job, long applicationCount) {
        return new JobView(
                String.valueOf(job.getId()),
                job.getJobCode(),
                job.getTitle(),
                IdSupport.asString(job.getDeptId()),
                job.getDeptName(),
                job.getJdText(),
                readJson(job.getRequirementsJson()),
                job.getHeadcount(),
                job.getSalaryRange(),
                job.getStatus(),
                IdSupport.asString(job.getOwnerHrId()),
                toInstant(job.getPublishedAt()),
                toInstant(job.getCreatedAt()),
                toInstant(job.getUpdatedAt()),
                job.getVersion(),
                applicationCount);
    }

    private JobView toView(JobRow row) {
        return new JobView(
                String.valueOf(row.getId()),
                row.getJobCode(),
                row.getTitle(),
                IdSupport.asString(row.getDeptId()),
                row.getDeptName(),
                row.getJdText(),
                readJson(row.getRequirementsJson()),
                row.getHeadcount(),
                row.getSalaryRange(),
                row.getStatus(),
                IdSupport.asString(row.getOwnerHrId()),
                toInstant(row.getPublishedAt()),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()),
                row.getVersion(),
                row.getApplicationCount() == null ? 0L : row.getApplicationCount());
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位要求 JSON 格式非法");
        }
    }

    private JsonNode readJson(String json) {
        if (!hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static long currentUserId() {
        return Long.parseLong(UserContext.requireUserId());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
