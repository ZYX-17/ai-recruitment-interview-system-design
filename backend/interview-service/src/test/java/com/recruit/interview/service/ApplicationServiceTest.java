package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.ApplicationStageChangedPayload;
import com.recruit.interview.dto.ApplicationActionForm;
import com.recruit.interview.dto.ApplicationCreateForm;
import com.recruit.interview.dto.ApplicationTimelineView;
import com.recruit.interview.dto.ApplicationView;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 投递创建/动作/时间线（P4-4~P4-7）。
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final String FLOW = "[{\"stageNo\":\"SCREEN\",\"stageName\":\"初筛\",\"mode\":\"ONLINE\",\"rounds\":1},"
            + "{\"stageNo\":\"FIRST\",\"stageName\":\"一面\",\"mode\":\"ONLINE\",\"rounds\":1}]";

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationStageHistoryMapper historyMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobStageTemplateMapper templateMapper;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ApplicationActionPermission actionPermission;
    @Mock
    private IdempotencyService idempotencyService;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationMapper, historyMapper, jobMapper, templateMapper,
                outboxPublisher, new ObjectMapper(), dataScopeService, new ApplicationStateMachine(),
                actionPermission, idempotencyService);
        UserContext.set("10001", List.of("HR_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createCopiesStageFlowAndWritesInitialHistory() {
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJobAndPerson(50001L, 60001L)).thenReturn(0L);
        when(templateMapper.selectActiveByJobId(50001L)).thenReturn(template());
        when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
            Application application = invocation.getArgument(0);
            application.setId(51001L);
            return 1;
        });

        ApplicationView view = service.create(createForm(), null);

        assertThat(view.id()).isEqualTo("51001");
        assertThat(view.currentStage()).isEqualTo("SCREEN");
        assertThat(view.status()).isEqualTo(ApplicationStateMachine.IN_PROGRESS);
        assertThat(view.stageFlow()).hasSize(2);
        assertThat(view.source()).isEqualTo("PORTAL");
        ArgumentCaptor<ApplicationStageHistory> history =
                ArgumentCaptor.forClass(ApplicationStageHistory.class);
        verify(historyMapper).insert(history.capture());
        assertThat(history.getValue().getStatus()).isEqualTo(ApplicationStateMachine.IN_PROGRESS);
        assertThat(history.getValue().getResult()).isEqualTo(ApplicationStateMachine.RESULT_PENDING);
    }

    @Test
    void createRejectsUnpublishedJob() {
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_DRAFT));
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(applicationMapper, never()).insert(any(Application.class));
    }

    @Test
    void createRejectsDuplicateApplication() {
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJobAndPerson(50001L, 60001L)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void createRejectsJobWithoutActiveTemplate() {
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJobAndPerson(50001L, 60001L)).thenReturn(0L);
        when(templateMapper.selectActiveByJobId(50001L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void advanceMovesStageAndPublishesStageChanged() {
        Application application = application(51001L, 50001L, "SCREEN", 0);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.updateStateWithVersion(any(Application.class))).thenReturn(1);

        ApplicationView view = service.action(51001L, actionForm("ADVANCE", 0));

        assertThat(view.currentStage()).isEqualTo("FIRST");
        assertThat(view.status()).isEqualTo(ApplicationStateMachine.IN_PROGRESS);
        assertThat(view.roundNo()).isZero();
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.STAGE_CHANGED), eq("51001"), eq("application"),
                payload.capture());
        assertThat(((ApplicationStageChangedPayload) payload.getValue()).fromStage()).isEqualTo("SCREEN");
        assertThat(((ApplicationStageChangedPayload) payload.getValue()).toStage()).isEqualTo("FIRST");
    }

    @Test
    void actionReportsVersionConflictWithDistinctMessage() {
        Application application = application(51001L, 50001L, "SCREEN", 5);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.action(51001L, actionForm("ADVANCE", 0)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
                    assertThat(biz.getMessage()).contains("版本冲突");
                });
    }

    @Test
    void rejectRequiresReason() {
        Application application = application(51001L, 50001L, "SCREEN", 0);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.action(51001L, actionForm("REJECT", 0)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void unknownActionIsRejectedBeforeWriting() {
        assertThatThrownBy(() -> service.action(51001L, actionForm("UNKNOWN", 0)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(applicationMapper, never()).updateStateWithVersion(any(Application.class));
    }

    @Test
    void transferCreatesTargetApplicationAndKeepsHistory() {
        Application source = application(51001L, 50001L, "SCREEN", 0);
        when(applicationMapper.selectById(51001L)).thenReturn(source);
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(jobMapper.selectById(50002L)).thenReturn(job(50002L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJobAndPerson(50002L, 60001L)).thenReturn(0L);
        when(templateMapper.selectActiveByJobId(50002L)).thenReturn(template());
        when(applicationMapper.updateStateWithVersion(any(Application.class))).thenReturn(1);
        when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
            Application target = invocation.getArgument(0);
            target.setId(51002L);
            return 1;
        });

        ApplicationActionForm form = actionForm("TRANSFER", 0);
        form.setTargetJobId("50002");
        ApplicationView view = service.action(51001L, form);

        assertThat(view.status()).isEqualTo(ApplicationStateMachine.TRANSFERRED);
        verify(historyMapper, times(2)).insert(any(ApplicationStageHistory.class));
        ArgumentCaptor<Application> inserted = ArgumentCaptor.forClass(Application.class);
        verify(applicationMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getJobId()).isEqualTo(50002L);
        assertThat(inserted.getValue().getStatus()).isEqualTo(ApplicationStateMachine.IN_PROGRESS);
        verify(outboxPublisher).publish(eq(EventTypes.STAGE_CHANGED), eq("51001"), eq("application"),
                any());
    }

    @Test
    void timelineResolvesStageNameFromSnapshotFlow() {
        when(applicationMapper.selectById(51001L)).thenReturn(application(51001L, 50001L, "SCREEN", 0));
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());
        ApplicationStageHistory history = new ApplicationStageHistory();
        history.setStageNo("SCREEN");
        history.setRoundNo(0);
        history.setStatus(ApplicationStateMachine.IN_PROGRESS);
        history.setResult(ApplicationStateMachine.RESULT_PENDING);
        history.setOperatorId(10001L);
        when(historyMapper.selectByApplicationId(51001L)).thenReturn(List.of(history));

        List<ApplicationTimelineView> timeline = service.timeline(51001L);

        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).stageName()).isEqualTo("初筛");
    }

    @Test
    void createKeepsPortalRemarkInInitialHistory() {
        when(jobMapper.selectById(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED));
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJobAndPerson(50001L, 60001L)).thenReturn(0L);
        when(templateMapper.selectActiveByJobId(50001L)).thenReturn(template());
        when(applicationMapper.insert(any(Application.class))).thenAnswer(invocation -> {
            Application application = invocation.getArgument(0);
            application.setId(51001L);
            return 1;
        });

        service.create(createForm(), null, "内推：老同事推荐");

        ArgumentCaptor<ApplicationStageHistory> history =
                ArgumentCaptor.forClass(ApplicationStageHistory.class);
        verify(historyMapper).insert(history.capture());
        assertThat(history.getValue().getComment()).contains("内推：老同事推荐");
    }

    @Test
    void portalWithdrawMarksWithdrawnWithoutInterviewPermission() {
        Application application = application(51001L, 50001L, "SCREEN", 0);
        application.setPersonId(10006L);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        when(applicationMapper.updateStateWithVersion(any(Application.class))).thenReturn(1);
        UserContext.set("10006", List.of("CANDIDATE"));

        ApplicationView view = service.withdrawForCandidate(51001L, 10006L);

        assertThat(view.status()).isEqualTo(ApplicationStateMachine.WITHDRAWN);
        verify(actionPermission, never()).require(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void portalWithdrawRejectsOtherCandidate() {
        Application application = application(51001L, 50001L, "SCREEN", 0);
        application.setPersonId(60001L);
        when(applicationMapper.selectById(51001L)).thenReturn(application);

        assertThatThrownBy(() -> service.withdrawForCandidate(51001L, 10006L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN));
        verify(applicationMapper, never()).updateStateWithVersion(any(Application.class));
    }

    @Test
    void portalWithdrawIsIdempotentWhenAlreadyWithdrawn() {
        Application application = application(51001L, 50001L, "SCREEN", 3);
        application.setPersonId(10006L);
        application.setStatus(ApplicationStateMachine.WITHDRAWN);
        when(applicationMapper.selectById(51001L)).thenReturn(application);

        ApplicationView view = service.withdrawForCandidate(51001L, 10006L);

        assertThat(view.status()).isEqualTo(ApplicationStateMachine.WITHDRAWN);
        verify(applicationMapper, never()).updateStateWithVersion(any(Application.class));
        verify(historyMapper, never()).insert(any(ApplicationStageHistory.class));
    }

    private static ApplicationCreateForm createForm() {
        ApplicationCreateForm form = new ApplicationCreateForm();
        form.setJobId("50001");
        form.setPersonId("60001");
        return form;
    }

    private static ApplicationActionForm actionForm(String action, int version) {
        ApplicationActionForm form = new ApplicationActionForm();
        form.setAction(action);
        form.setVersion(version);
        return form;
    }

    private static Application application(long id, long jobId, String stage, int version) {
        Application application = new Application();
        application.setId(id);
        application.setJobId(jobId);
        application.setJobTitle("Java 开发");
        application.setPersonId(60001L);
        application.setPersonName("张三");
        application.setCurrentStage(stage);
        application.setRoundNo(0);
        application.setStageFlow(FLOW);
        application.setStatus(ApplicationStateMachine.IN_PROGRESS);
        application.setSource("PORTAL");
        application.setVersion(version);
        return application;
    }

    private static Job job(long id, String status) {
        Job job = new Job();
        job.setId(id);
        job.setJobCode("JOB-" + id);
        job.setTitle("Java 开发");
        job.setStatus(status);
        job.setOwnerHrId(10001L);
        return job;
    }

    private static JobStageTemplate template() {
        JobStageTemplate template = new JobStageTemplate();
        template.setId(90001L);
        template.setJobId(50001L);
        template.setTemplateVer(1);
        template.setStatus("ACTIVE");
        template.setStageFlow(FLOW);
        template.setScoreDims("[]");
        return template;
    }

    private static DataScopeService.DataScope allScope() {
        return new DataScopeService.DataScope("ALL", null, List.of(), 10001L);
    }
}
