package com.recruit.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.ApplicationStageChangedPayload;
import com.recruit.interview.dto.ApplicationActionForm;
import com.recruit.interview.dto.ApplicationCreateForm;
import com.recruit.interview.dto.ApplicationPageQuery;
import com.recruit.interview.dto.ApplicationTimelineView;
import com.recruit.interview.dto.ApplicationView;
import com.recruit.interview.dto.StageDef;
import com.recruit.interview.entity.Application;
import com.recruit.interview.entity.ApplicationStageHistory;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.JobStageTemplate;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.ApplicationStageHistoryMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.JobStageTemplateMapper;
import com.recruit.interview.support.ApplicationActionPermission;
import com.recruit.interview.support.IdSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 投递管理（P4-4~P4-7）。
 *
 * <p>创建时复制岗位 ACTIVE 模板的 {@code stage_flow}，后续模板变更不影响在途投递；
 * 所有动作走显式状态机 + {@code version} 乐观锁，冲突统一 409 {@code ITV3102}，
 * 并在同一事务写 {@code application_stage_history} 与 Outbox。</p>
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final Set<String> ACTIONS = Set.of(
            ApplicationActionPermission.ACTION_ADVANCE,
            ApplicationActionPermission.ACTION_PASS,
            ApplicationActionPermission.ACTION_REJECT,
            ApplicationActionPermission.ACTION_HOLD,
            ApplicationActionPermission.ACTION_TRANSFER,
            ApplicationActionPermission.ACTION_WITHDRAW,
            ApplicationActionPermission.ACTION_HIRE);
    private static final Set<String> SOURCES = Set.of("PORTAL", "IMPORT");
    private static final TypeReference<List<StageDef>> STAGE_FLOW_TYPE = new TypeReference<>() {
    };

    private final ApplicationMapper applicationMapper;
    private final ApplicationStageHistoryMapper historyMapper;
    private final JobMapper jobMapper;
    private final JobStageTemplateMapper templateMapper;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationActionPermission actionPermission;
    private final IdempotencyService idempotencyService;

    /**
     * 投递分页。
     *
     * @param query 分页与过滤条件
     * @return 分页投递
     */
    public PageResult<ApplicationView> page(ApplicationPageQuery query) {
        DataScopeService.DataScope scope = dataScopeService.current();
        String status = trimToNull(query.getStatus());
        status = status == null ? null : status.toUpperCase(Locale.ROOT);
        Long jobId = IdSupport.optionalLong(query.getJobId(), "jobId");
        Long personId = IdSupport.optionalLong(query.getPersonId(), "personId");
        String keyword = trimToNull(query.getKeyword());
        String sqlScope = scope.sqlScope();

        long total = applicationMapper.countApplicationPage(keyword, status, jobId, personId,
                sqlScope, scope.deptIds(), scope.userId());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<Application> rows = applicationMapper.selectApplicationPage(keyword, status, jobId,
                personId, sqlScope, scope.deptIds(), scope.userId(), query.getPageSize(), offset);
        List<ApplicationView> views = new ArrayList<>(rows.size());
        for (Application row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 投递详情。
     *
     * @param id 投递 ID
     * @return 投递详情
     */
    public ApplicationView detail(long id) {
        return toView(loadVisible(id));
    }

    /**
     * 投递时间线。
     *
     * @param id 投递 ID
     * @return 历史记录（按操作时间升序）
     */
    public List<ApplicationTimelineView> timeline(long id) {
        Application application = loadVisible(id);
        Map<String, String> stageNames = readFlowOrEmpty(application.getStageFlow()).stream()
                .collect(Collectors.toMap(StageDef::getStageNo, StageDef::getStageName, (a, b) -> a));
        List<ApplicationStageHistory> rows = historyMapper.selectByApplicationId(id);
        List<ApplicationTimelineView> views = new ArrayList<>(rows.size());
        for (ApplicationStageHistory row : rows) {
            views.add(new ApplicationTimelineView(
                    row.getStageNo(),
                    stageNames.getOrDefault(row.getStageNo(), row.getStageNo()),
                    row.getRoundNo(),
                    row.getStatus(),
                    row.getResult(),
                    IdSupport.asString(row.getOperatorId()),
                    row.getComment(),
                    toInstant(row.getOperatedAt())));
        }
        return views;
    }

    /**
     * 创建投递：岗位必须 PUBLISHED，同岗位同候选人唯一。
     *
     * @param form 创建入参
     * @param key  Idempotency-Key，可为空
     * @return 新投递
     */
    @Transactional
    public ApplicationView create(ApplicationCreateForm form, String key) {
        if (hasText(key)) {
            return idempotencyService.execute("interview.application.create", key,
                    ApplicationView.class, () -> doCreate(form));
        }
        return doCreate(form);
    }

    private ApplicationView doCreate(ApplicationCreateForm form) {
        long jobId = IdSupport.requireLong(form.getJobId(), "jobId");
        long personId = IdSupport.requireLong(form.getPersonId(), "personId");
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        requireVisible(job);
        if (!JobService.STATUS_PUBLISHED.equals(job.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位未发布，不能投递");
        }
        if (applicationMapper.countByJobAndPerson(jobId, personId) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该候选人在此岗位已有投递");
        }
        JobStageTemplate template = templateMapper.selectActiveByJobId(jobId);
        if (template == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位未配置生效流程模板");
        }
        List<StageDef> flow = readFlowRequired(template.getStageFlow());
        if (flow.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "岗位流程模板为空");
        }

        long operatorId = currentUserId();
        String source = normalizeSource(form.getSource());
        Application application = new Application();
        application.setJobId(jobId);
        application.setJobTitle(job.getTitle());
        application.setPersonId(personId);
        application.setPersonName(hasText(form.getPersonName())
                ? form.getPersonName().trim() : "候选人-" + personId);
        application.setCurrentStage(flow.get(0).getStageNo());
        application.setRoundNo(0);
        application.setStageFlow(template.getStageFlow());
        application.setMatchScore(form.getMatchScore());
        application.setStatus(ApplicationStateMachine.IN_PROGRESS);
        application.setSource(source);
        application.setVersion(0);
        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该候选人在此岗位已有投递");
        }

        recordHistory(application.getId(), flow.get(0).getStageNo(), 0,
                ApplicationStateMachine.IN_PROGRESS, ApplicationStateMachine.RESULT_PENDING,
                "创建投递", operatorId);
        Application saved = applicationMapper.selectById(application.getId());
        return toView(saved == null ? application : saved);
    }

    /**
     * 执行投递动作。
     *
     * @param id   投递 ID
     * @param form 动作入参
     * @return 动作后的投递
     */
    @Transactional
    public ApplicationView action(long id, ApplicationActionForm form) {
        String action = form.getAction() == null
                ? "" : form.getAction().trim().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的投递动作: " + form.getAction());
        }
        actionPermission.require(action);

        Application application = loadVisible(id);
        if (!Objects.equals(application.getVersion(), form.getVersion())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        if (ApplicationActionPermission.ACTION_REJECT.equals(action) && !hasText(form.getComment())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "淘汰必须填写原因");
        }
        if (ApplicationActionPermission.ACTION_TRANSFER.equals(action)) {
            return transfer(application, form, action);
        }

        List<StageDef> flow = readFlowRequired(application.getStageFlow());
        ApplicationStateMachine.TransitionResult transition = stateMachine.apply(
                application.getStatus(), application.getCurrentStage(), action,
                form.getTargetStage(), flow);

        String fromStage = application.getCurrentStage();
        String fromStatus = application.getStatus();
        int roundNo = preservesRound(action)
                ? (application.getRoundNo() == null ? 0 : application.getRoundNo())
                : transition.roundNo();

        application.setCurrentStage(transition.stageNo());
        application.setRoundNo(roundNo);
        application.setStatus(transition.status());
        if (ApplicationActionPermission.ACTION_REJECT.equals(action)) {
            application.setRejectReason(form.getComment().trim());
        }
        application.setVersion(form.getVersion());
        if (applicationMapper.updateStateWithVersion(application) == 0) {
            throw conflictOrIllegal(id, form.getVersion());
        }

        long operatorId = currentUserId();
        recordHistory(id, application.getCurrentStage(), roundNo, transition.status(),
                transition.result(), trimToNull(form.getComment()), operatorId);
        publishStageChanged(fromStage, fromStatus, application, roundNo, transition.result(), operatorId);
        return toView(applicationMapper.selectById(id));
    }

    private ApplicationView transfer(Application application, ApplicationActionForm form, String action) {
        if (!Objects.equals(application.getVersion(), form.getVersion())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        long targetJobId = IdSupport.requireLong(form.getTargetJobId(), "targetJobId");
        if (targetJobId == application.getJobId()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "转岗目标不能是当前岗位");
        }
        Job targetJob = jobMapper.selectById(targetJobId);
        if (targetJob == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "目标岗位不存在");
        }
        requireVisible(targetJob);
        if (!JobService.STATUS_PUBLISHED.equals(targetJob.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标岗位未发布，不能转入");
        }
        if (applicationMapper.countByJobAndPerson(targetJobId, application.getPersonId()) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该候选人在目标岗位已有投递");
        }
        JobStageTemplate targetTemplate = templateMapper.selectActiveByJobId(targetJobId);
        if (targetTemplate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标岗位未配置生效流程模板");
        }
        List<StageDef> targetFlow = readFlowRequired(targetTemplate.getStageFlow());
        if (targetFlow.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "目标岗位流程模板为空");
        }

        List<StageDef> flow = readFlowRequired(application.getStageFlow());
        ApplicationStateMachine.TransitionResult transition = stateMachine.apply(
                application.getStatus(), application.getCurrentStage(), action, null, flow);

        String fromStage = application.getCurrentStage();
        String fromStatus = application.getStatus();
        int roundNo = application.getRoundNo() == null ? 0 : application.getRoundNo();
        application.setCurrentStage(transition.stageNo());
        application.setRoundNo(roundNo);
        application.setStatus(transition.status());
        application.setVersion(form.getVersion());
        if (applicationMapper.updateStateWithVersion(application) == 0) {
            throw conflictOrIllegal(application.getId(), form.getVersion());
        }

        long operatorId = currentUserId();
        Application target = new Application();
        target.setJobId(targetJob.getId());
        target.setJobTitle(targetJob.getTitle());
        target.setPersonId(application.getPersonId());
        target.setPersonName(application.getPersonName());
        target.setCurrentStage(targetFlow.get(0).getStageNo());
        target.setRoundNo(0);
        target.setStageFlow(targetTemplate.getStageFlow());
        target.setMatchScore(application.getMatchScore());
        target.setStatus(ApplicationStateMachine.IN_PROGRESS);
        target.setSource(application.getSource());
        target.setVersion(0);
        try {
            applicationMapper.insert(target);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该候选人在目标岗位已有投递");
        }

        String transferComment = "转岗至 " + targetJob.getTitle();
        if (hasText(form.getComment())) {
            transferComment = transferComment + "：" + form.getComment().trim();
        }
        recordHistory(application.getId(), application.getCurrentStage(), roundNo,
                ApplicationStateMachine.TRANSFERRED, null, transferComment, operatorId);
        recordHistory(target.getId(), target.getCurrentStage(), 0,
                ApplicationStateMachine.IN_PROGRESS, ApplicationStateMachine.RESULT_PENDING,
                "由岗位 " + application.getJobTitle() + " 转入", operatorId);
        publishStageChanged(fromStage, fromStatus, application, roundNo, null, operatorId);
        return toView(applicationMapper.selectById(application.getId()));
    }

    private void publishStageChanged(String fromStage, String fromStatus, Application application,
                                     int roundNo, String result, long operatorId) {
        boolean changed = !Objects.equals(fromStage, application.getCurrentStage())
                || !Objects.equals(fromStatus, application.getStatus());
        if (!changed) {
            return;
        }
        outboxPublisher.publish(EventTypes.STAGE_CHANGED, String.valueOf(application.getId()),
                "application", new ApplicationStageChangedPayload(
                        String.valueOf(application.getId()), fromStage, application.getCurrentStage(),
                        roundNo, result, String.valueOf(operatorId)));
    }

    private BizException conflictOrIllegal(long id, int expectedVersion) {
        Application latest = applicationMapper.selectById(id);
        if (latest != null && !Objects.equals(latest.getVersion(), expectedVersion)) {
            return new BizException(ErrorCode.ILLEGAL_TRANSITION, "版本冲突，请刷新后重试");
        }
        return new BizException(ErrorCode.ILLEGAL_TRANSITION, "状态流转非法，请刷新后重试");
    }

    private void recordHistory(Long applicationId, String stageNo, int roundNo, String status,
                               String result, String comment, long operatorId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ApplicationStageHistory history = new ApplicationStageHistory();
        history.setApplicationId(applicationId);
        history.setStageNo(stageNo);
        history.setRoundNo(roundNo);
        history.setStatus(status);
        history.setResult(result);
        history.setOperatorId(operatorId);
        history.setComment(comment);
        history.setOperatedAt(now);
        history.setCreatedAt(now);
        historyMapper.insert(history);
    }

    private Application loadVisible(long id) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new BizException(ErrorCode.APP_NOT_FOUND);
        }
        Job job = jobMapper.selectById(application.getJobId());
        if (job == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该投递");
        }
        requireVisible(job);
        return application;
    }

    private void requireVisible(Job job) {
        DataScopeService.DataScope scope = dataScopeService.current();
        if (!scope.canSee(job.getDeptId(), job.getOwnerHrId())) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "无权访问该岗位投递");
        }
    }

    private List<StageDef> readFlowRequired(String json) {
        try {
            List<StageDef> flow = objectMapper.readValue(json, STAGE_FLOW_TYPE);
            return flow == null ? List.of() : flow;
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "投递流程配置损坏", e);
        }
    }

    private List<StageDef> readFlowOrEmpty(String json) {
        try {
            List<StageDef> flow = objectMapper.readValue(json, STAGE_FLOW_TYPE);
            return flow == null ? List.of() : flow;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private ApplicationView toView(Application application) {
        return new ApplicationView(
                String.valueOf(application.getId()),
                String.valueOf(application.getJobId()),
                application.getJobTitle(),
                String.valueOf(application.getPersonId()),
                application.getPersonName(),
                application.getCurrentStage(),
                application.getRoundNo(),
                readFlowOrEmpty(application.getStageFlow()),
                application.getMatchScore(),
                application.getStatus(),
                application.getRejectReason(),
                application.getSource(),
                toInstant(application.getCreatedAt()),
                toInstant(application.getUpdatedAt()),
                application.getVersion());
    }

    private static boolean preservesRound(String action) {
        return !ApplicationActionPermission.ACTION_ADVANCE.equals(action)
                && !ApplicationActionPermission.ACTION_PASS.equals(action);
    }

    private static String normalizeSource(String value) {
        if (value == null || value.isBlank()) {
            return "PORTAL";
        }
        String source = value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.contains(source)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "投递来源非法");
        }
        return source;
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
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
