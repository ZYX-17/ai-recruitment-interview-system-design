package com.recruit.interview.service;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 岗位服务（P4-1/P4-2/P4-7）。
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobStageTemplateMapper templateMapper;
    @Mock
    private EvaluationDimensionMapper dimensionMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private IdempotencyService idempotencyService;

    private JobService service;

    @BeforeEach
    void setUp() {
        service = new JobService(jobMapper, templateMapper, dimensionMapper, applicationMapper,
                outboxPublisher, new ObjectMapper(), dataScopeService, idempotencyService);
        UserContext.set("10001", List.of("HR_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createDefaultsOwnerToCurrentUserAndDraftStatus() {
        when(jobMapper.selectByJobCode("JOB-1")).thenReturn(null);
        when(jobMapper.insert(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(50001L);
            return 1;
        });

        JobCreateForm form = new JobCreateForm();
        form.setJobCode("JOB-1");
        form.setTitle("Java 开发");
        form.setRequirements(Map.of("minYears", 3));

        JobView view = service.create(form, null);

        assertThat(view.id()).isEqualTo("50001");
        assertThat(view.status()).isEqualTo(JobService.STATUS_DRAFT);
        assertThat(view.ownerHrId()).isEqualTo("10001");
        assertThat(view.version()).isZero();
        assertThat(view.requirements().get("minYears").asInt()).isEqualTo(3);
    }

    @Test
    void createRejectsDuplicateJobCode() {
        when(jobMapper.selectByJobCode("JOB-1")).thenReturn(new Job());

        JobCreateForm form = new JobCreateForm();
        form.setJobCode("JOB-1");
        form.setTitle("Java 开发");

        assertThatThrownBy(() -> service.create(form, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(jobMapper, never()).insert(any(Job.class));
    }

    @Test
    void publishWritesOutboxEventInSameServiceCall() {
        Job draft = job(50001L, JobService.STATUS_DRAFT, 0);
        Job published = job(50001L, JobService.STATUS_PUBLISHED, 1);
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(draft);
        when(jobMapper.updateStatus(eq(50001L), eq(JobService.STATUS_PUBLISHED), any(), eq(0)))
                .thenReturn(1);
        when(jobMapper.selectById(50001L)).thenReturn(published);
        when(jobMapper.countApplications(50001L)).thenReturn(0L);
        when(dataScopeService.current()).thenReturn(allScope());

        JobView view = service.publish(50001L, 0);

        assertThat(view.status()).isEqualTo(JobService.STATUS_PUBLISHED);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.JOB_PUBLISHED), eq("50001"), eq("job"),
                payload.capture());
        assertThat(payload.getValue()).isInstanceOf(JobPublishedPayload.class);
        assertThat(((JobPublishedPayload) payload.getValue()).status()).isEqualTo("PUBLISHED");
    }

    @Test
    void closeWritesJobUpdatedEvent() {
        Job published = job(50001L, JobService.STATUS_PUBLISHED, 3);
        Job closed = job(50001L, JobService.STATUS_CLOSED, 4);
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(published);
        when(jobMapper.updateStatus(eq(50001L), eq(JobService.STATUS_CLOSED), isNull(), eq(3)))
                .thenReturn(1);
        when(jobMapper.selectById(50001L)).thenReturn(closed);
        when(jobMapper.countApplications(50001L)).thenReturn(2L);
        when(dataScopeService.current()).thenReturn(allScope());

        service.close(50001L, 3);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.JOB_UPDATED), eq("50001"), eq("job"),
                payload.capture());
        assertThat(((JobUpdatedPayload) payload.getValue()).status()).isEqualTo("CLOSED");
    }

    @Test
    void updatePublishedJobPublishesChangedFields() {
        Job current = job(50001L, JobService.STATUS_PUBLISHED, 2);
        Job updated = job(50001L, JobService.STATUS_PUBLISHED, 3);
        updated.setTitle("高级 Java 开发");
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(current);
        when(dataScopeService.current()).thenReturn(allScope());
        when(jobMapper.updateWithVersion(any(Job.class))).thenReturn(1);
        when(jobMapper.selectById(50001L)).thenReturn(updated);
        when(jobMapper.countApplications(50001L)).thenReturn(1L);

        JobUpdateForm form = new JobUpdateForm();
        form.setTitle("高级 Java 开发");
        form.setVersion(2);
        JobView view = service.update(50001L, form);

        assertThat(view.title()).isEqualTo("高级 Java 开发");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.JOB_UPDATED), eq("50001"), eq("job"),
                payload.capture());
        JobUpdatedPayload event = (JobUpdatedPayload) payload.getValue();
        assertThat(event.version()).isEqualTo(3);
        assertThat(event.changedFields()).containsExactly("title");
    }

    @Test
    void updateRejectsVersionConflict() {
        Job current = job(50001L, JobService.STATUS_DRAFT, 2);
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(current);
        when(jobMapper.updateWithVersion(any(Job.class))).thenReturn(0);
        when(dataScopeService.current()).thenReturn(allScope());

        JobUpdateForm form = new JobUpdateForm();
        form.setTitle("新名称");
        form.setVersion(1);

        assertThatThrownBy(() -> service.update(50001L, form))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ILLEGAL_TRANSITION);
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void deleteOnlyAllowsDraftWithoutApplications() {
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(job(50001L, JobService.STATUS_PUBLISHED, 1));
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.delete(50001L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void deleteCleansTemplatesAndDimensions() {
        Job draft = job(50001L, JobService.STATUS_DRAFT, 0);
        JobStageTemplate template = new JobStageTemplate();
        template.setId(90001L);
        template.setJobId(50001L);
        when(jobMapper.selectByIdForUpdate(50001L)).thenReturn(draft);
        when(dataScopeService.current()).thenReturn(allScope());
        when(applicationMapper.countByJob(50001L)).thenReturn(0L);
        when(templateMapper.selectByJobId(50001L)).thenReturn(List.of(template));

        service.delete(50001L);

        verify(dimensionMapper).deleteByTemplateId(90001L);
        verify(templateMapper).deleteById(90001L);
        verify(jobMapper).deleteById(50001L);
    }

    @Test
    void pagePassesDepartmentSubtreeToSql() {
        when(dataScopeService.current()).thenReturn(new DataScopeService.DataScope(
                "DEPARTMENT", 20001L, List.of(20001L, 20002L), 10001L));
        when(jobMapper.countJobPage(null, null, null, "DEPARTMENT",
                List.of(20001L, 20002L), 10001L)).thenReturn(0L);

        JobPageQuery query = new JobPageQuery();
        PageResult<JobView> result = service.page(query);

        assertThat(result.getTotal()).isZero();
        verify(jobMapper).countJobPage(null, null, null, "DEPARTMENT",
                List.of(20001L, 20002L), 10001L);
    }

    @Test
    void writeMethodsAreTransactional() throws Exception {
        assertThat(JobService.class.getMethod("create", JobCreateForm.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(JobService.class.getMethod("update", long.class, JobUpdateForm.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(JobService.class.getMethod("publish", long.class, int.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(JobService.class.getMethod("close", long.class, int.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private static Job job(long id, String status, int version) {
        Job job = new Job();
        job.setId(id);
        job.setJobCode("JOB-2026-001");
        job.setTitle("Java 开发");
        job.setStatus(status);
        job.setOwnerHrId(10001L);
        job.setHeadcount(2);
        job.setVersion(version);
        return job;
    }

    private static DataScopeService.DataScope allScope() {
        return new DataScopeService.DataScope("ALL", null, List.of(), 10001L);
    }
}
