package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.InterviewCancelledPayload;
import com.recruit.contracts.event.payload.InterviewRescheduledPayload;
import com.recruit.contracts.event.payload.InterviewScheduledPayload;
import com.recruit.interview.dto.CalendarEventView;
import com.recruit.interview.dto.CalendarQuery;
import com.recruit.interview.dto.ScheduleBatchForm;
import com.recruit.interview.dto.ScheduleCreateForm;
import com.recruit.interview.dto.SchedulePageQuery;
import com.recruit.interview.dto.ScheduleUpdateForm;
import com.recruit.interview.dto.ScheduleView;
import com.recruit.interview.entity.Application;
import com.recruit.interview.entity.Interview;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.ScheduleSlot;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.ScheduleSlotMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import com.recruit.interview.mapper.row.ScheduleConflictRow;
import com.recruit.interview.support.ScheduleConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排期创建/预检/改期/取消/批量（P5-2~P5-5）。
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private ScheduleSlotMapper slotMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private IdempotencyService idempotencyService;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(interviewMapper, slotMapper, applicationMapper, jobMapper,
                outboxPublisher, new ObjectMapper(), dataScopeService, idempotencyService);
        UserContext.set("10001", List.of("HR_ADMIN"), "HR", "trace");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createWritesSlotsAndPublishesScheduled() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(interviewMapper.countByApplicationAndRound(51001L, 1)).thenReturn(0L);
        when(slotMapper.selectConflicts(anyList(), anyList(), anyList())).thenReturn(List.of());
        when(interviewMapper.insert(any(Interview.class))).thenAnswer(invocation -> {
            Interview interview = invocation.getArgument(0);
            interview.setId(90001L);
            return 1;
        });

        ScheduleView view = service.create(createForm(), null);

        assertThat(view.id()).isEqualTo("90001");
        assertThat(view.status()).isEqualTo(ScheduleService.STATUS_ROOM_PENDING);
        assertThat(view.interviewerIds()).containsExactly("10002", "10003");
        ArgumentCaptor<Interview> interviewCaptor = ArgumentCaptor.forClass(Interview.class);
        verify(interviewMapper).insert(interviewCaptor.capture());
        assertThat(interviewCaptor.getValue().getStartTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 1, 0));
        assertThat(interviewCaptor.getValue().getInterviewerIds()).isEqualTo("[\"10002\",\"10003\"]");
        verify(slotMapper, times(6)).insert(any(ScheduleSlot.class));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.INTERVIEW_SCHEDULED), eq("90001"),
                eq("interview"), payloadCaptor.capture());
        InterviewScheduledPayload payload = (InterviewScheduledPayload) payloadCaptor.getValue();
        assertThat(payload.interviewerIds()).containsExactly("10002", "10003");
        assertThat(payload.candidateId()).isEqualTo("60001");
        assertThat(payload.jobTitle()).isEqualTo("Java 开发工程师");
    }

    @Test
    void createRejectsConflictBeforeWriting() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(interviewMapper.countByApplicationAndRound(51001L, 1)).thenReturn(0L);
        when(slotMapper.selectConflicts(anyList(), anyList(), anyList()))
                .thenReturn(List.of(conflict()));

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOfSatisfying(ScheduleConflictException.class,
                        ex -> assertThat(ex.getConflicts()).hasSize(1));
        verify(interviewMapper, never()).insert(any(Interview.class));
        verify(slotMapper, never()).insert(any(ScheduleSlot.class));
    }

    @Test
    void createRejectsTimeNotAlignedToThirtyMinutes() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        ScheduleCreateForm form = createForm();
        form.setStartTime(Instant.parse("2026-10-20T01:15:00Z"));

        assertThatThrownBy(() -> service.create(form, null))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        verify(interviewMapper, never()).insert(any(Interview.class));
    }

    @Test
    void createMapsDuplicateSlotKeyToConflictWithDetails() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(interviewMapper.countByApplicationAndRound(51001L, 1)).thenReturn(0L);
        when(slotMapper.selectConflicts(anyList(), anyList(), anyList()))
                .thenReturn(List.of())
                .thenReturn(List.of(conflict()));
        when(interviewMapper.insert(any(Interview.class))).thenAnswer(invocation -> {
            Interview interview = invocation.getArgument(0);
            interview.setId(90001L);
            return 1;
        });
        when(slotMapper.insert(any(ScheduleSlot.class)))
                .thenThrow(new DuplicateKeyException("uk_slot"));

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOfSatisfying(ScheduleConflictException.class,
                        ex -> assertThat(ex.getConflicts()).hasSize(1));
        verify(interviewMapper).insert(any(Interview.class));
    }

    @Test
    void createRejectsInterviewLongerThanTwentyFourHours() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        ScheduleCreateForm form = createForm();
        form.setEndTime(Instant.parse("2026-10-21T02:00:00Z"));

        assertThatThrownBy(() -> service.create(form, null))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void createRejectsDuplicateRound() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(interviewMapper.countByApplicationAndRound(51001L, 1)).thenReturn(1L);

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
        verify(interviewMapper, never()).insert(any(Interview.class));
    }

    @Test
    void pageAppliesDepartmentScopeAndInterviewerFilter() {
        when(dataScopeService.current()).thenReturn(
                new DataScopeService.DataScope("DEPARTMENT", 20001L, List.of(20001L), 10001L));
        when(interviewMapper.countSchedulePage(eq("ROOM_PENDING"), isNull(), isNull(),
                eq("10002"), isNull(), eq("DEPARTMENT"), eq(List.of(20001L)),
                eq(10001L), eq("10001"))).thenReturn(0L);
        SchedulePageQuery query = new SchedulePageQuery();
        query.setStatus("room_pending");
        query.setInterviewerId("10002");

        assertThat(service.page(query).getTotal()).isZero();
        verify(interviewMapper, never()).selectSchedulePage(any(), any(), any(), any(), any(),
                any(), any(), anyLong(), any(), anyInt(), anyLong());
    }

    @Test
    void calendarMapsRowsToEvents() {
        when(dataScopeService.current()).thenReturn(allScope());
        when(interviewMapper.selectCalendar(any(), any(), isNull(), isNull(), eq("ALL"),
                anyList(), eq(10001L), eq("10001"))).thenReturn(List.of(row(
                ScheduleService.STATUS_SCHEDULED, 0)));
        CalendarQuery query = new CalendarQuery();
        query.setDateFrom(LocalDate.of(2026, 10, 20));
        query.setDateTo(LocalDate.of(2026, 10, 21));

        List<CalendarEventView> events = service.calendar(query);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).id()).isEqualTo("90001");
        assertThat(events.get(0).interviewerIds()).containsExactly("10002", "10003");
    }

    @Test
    void updateDeletesOldSlotsThenInsertsAndPublishesRescheduled() {
        when(interviewMapper.selectRowById(90001L)).thenReturn(
                row(ScheduleService.STATUS_SCHEDULED, 3));
        when(dataScopeService.current()).thenReturn(allScope());
        when(slotMapper.selectByInterviewId(90001L)).thenReturn(List.of());
        when(slotMapper.selectConflicts(anyList(), anyList(), anyList())).thenReturn(List.of());
        when(interviewMapper.updateScheduleWithVersion(any(Interview.class))).thenReturn(1);

        ScheduleUpdateForm form = new ScheduleUpdateForm();
        form.setInterviewerIds(List.of("10002"));
        form.setStartTime(Instant.parse("2026-10-20T03:00:00Z"));
        form.setEndTime(Instant.parse("2026-10-20T04:00:00Z"));
        form.setMode("ONLINE");
        form.setRoomType("ONE_TO_ONE");
        form.setVersion(3);

        service.update(90001L, form);

        InOrder inOrder = org.mockito.Mockito.inOrder(slotMapper, interviewMapper);
        inOrder.verify(slotMapper).deleteByInterviewId(90001L);
        inOrder.verify(slotMapper, org.mockito.Mockito.atLeastOnce()).insert(any(ScheduleSlot.class));
        inOrder.verify(interviewMapper).updateScheduleWithVersion(any(Interview.class));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.INTERVIEW_RESCHEDULED), eq("90001"),
                eq("interview"), payloadCaptor.capture());
        InterviewRescheduledPayload payload = (InterviewRescheduledPayload) payloadCaptor.getValue();
        assertThat(payload.oldStartTime()).isEqualTo(Instant.parse("2026-10-20T01:00:00Z"));
        assertThat(payload.newStartTime()).isEqualTo(Instant.parse("2026-10-20T03:00:00Z"));
    }

    @Test
    void updateRejectsVersionMismatch() {
        when(interviewMapper.selectRowById(90001L)).thenReturn(
                row(ScheduleService.STATUS_SCHEDULED, 3));
        when(dataScopeService.current()).thenReturn(allScope());
        ScheduleUpdateForm form = new ScheduleUpdateForm();
        form.setInterviewerIds(List.of("10002"));
        form.setStartTime(Instant.parse("2026-10-20T03:00:00Z"));
        form.setEndTime(Instant.parse("2026-10-20T04:00:00Z"));
        form.setMode("ONLINE");
        form.setRoomType("ONE_TO_ONE");
        form.setVersion(2);

        assertThatThrownBy(() -> service.update(90001L, form))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
        verify(slotMapper, never()).deleteByInterviewId(90001L);
    }

    @Test
    void cancelIsIdempotentWhenAlreadyCancelled() {
        when(interviewMapper.selectRowById(90001L)).thenReturn(
                row(ScheduleService.STATUS_CANCELLED, 2));
        when(dataScopeService.current()).thenReturn(allScope());

        ScheduleView view = service.cancel(90001L, 99, null);

        assertThat(view.status()).isEqualTo(ScheduleService.STATUS_CANCELLED);
        verify(slotMapper, never()).deleteByInterviewId(90001L);
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void cancelRejectsInProgressInterview() {
        when(interviewMapper.selectRowById(90001L)).thenReturn(
                row(ScheduleService.STATUS_IN_PROGRESS, 1));
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.cancel(90001L, 1, null))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
        verify(slotMapper, never()).deleteByInterviewId(90001L);
    }

    @Test
    void cancelDeletesSlotsAndPublishesCancelled() {
        when(interviewMapper.selectRowById(90001L)).thenReturn(
                row(ScheduleService.STATUS_ROOM_PENDING, 0));
        when(dataScopeService.current()).thenReturn(allScope());
        when(interviewMapper.cancelWithVersion(90001L, "候选人临时有事", 0)).thenReturn(1);

        service.cancel(90001L, 0, "候选人临时有事");

        verify(slotMapper).deleteByInterviewId(90001L);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.INTERVIEW_CANCELLED), eq("90001"),
                eq("interview"), payloadCaptor.capture());
        InterviewCancelledPayload payload = (InterviewCancelledPayload) payloadCaptor.getValue();
        assertThat(payload.reason()).isEqualTo("候选人临时有事");
    }

    @Test
    void batchRejectsMoreThanFiftyItems() {
        ScheduleBatchForm form = new ScheduleBatchForm();
        form.setItems(Collections.nCopies(51, new ScheduleCreateForm()));

        assertThatThrownBy(() -> service.batch(form, null))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void batchRejectsAllConflictsBeforeWriting() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(interviewMapper.countByApplicationAndRound(51001L, 1)).thenReturn(0L);
        when(interviewMapper.countByApplicationAndRound(51001L, 2)).thenReturn(0L);
        when(slotMapper.selectConflicts(anyList(), anyList(), anyList()))
                .thenReturn(List.of())
                .thenReturn(List.of(conflict()));

        ScheduleCreateForm second = createForm();
        second.setRoundNo(2);
        ScheduleBatchForm form = new ScheduleBatchForm();
        form.setItems(new ArrayList<>(List.of(createForm(), second)));

        assertThatThrownBy(() -> service.batch(form, null))
                .isInstanceOfSatisfying(ScheduleConflictException.class,
                        ex -> assertThat(ex.getConflicts()).hasSize(1));
        verify(interviewMapper, never()).insert(any(Interview.class));
    }

    private void stubApplicationAndJob() {
        when(applicationMapper.selectById(51001L)).thenReturn(application());
        when(jobMapper.selectById(50001L)).thenReturn(job());
    }

    private static Application application() {
        Application application = new Application();
        application.setId(51001L);
        application.setJobId(50001L);
        application.setJobTitle("Java 开发工程师");
        application.setPersonId(60001L);
        application.setPersonName("张三");
        return application;
    }

    private static Job job() {
        Job job = new Job();
        job.setId(50001L);
        job.setTitle("Java 开发工程师");
        job.setDeptId(20001L);
        job.setOwnerHrId(10001L);
        job.setStatus(JobService.STATUS_PUBLISHED);
        return job;
    }

    private static DataScopeService.DataScope allScope() {
        return new DataScopeService.DataScope("ALL", 20001L, List.of(20001L), 10001L);
    }

    private static ScheduleCreateForm createForm() {
        ScheduleCreateForm form = new ScheduleCreateForm();
        form.setApplicationId("51001");
        form.setRoundNo(1);
        form.setInterviewerIds(List.of("10002", "10003"));
        form.setStartTime(Instant.parse("2026-10-20T01:00:00Z"));
        form.setEndTime(Instant.parse("2026-10-20T02:00:00Z"));
        form.setMode("ONLINE");
        form.setRoomType("ONE_TO_ONE");
        return form;
    }

    private static InterviewRow row(String status, int version) {
        InterviewRow row = new InterviewRow();
        row.setId(90001L);
        row.setApplicationId(51001L);
        row.setRoundNo(1);
        row.setInterviewerIds("[\"10002\",\"10003\"]");
        row.setStartTime(LocalDateTime.of(2026, 10, 20, 1, 0));
        row.setEndTime(LocalDateTime.of(2026, 10, 20, 2, 0));
        row.setMode("ONLINE");
        row.setRoomType("ONE_TO_ONE");
        row.setStatus(status);
        row.setVersion(version);
        row.setJobId(50001L);
        row.setJobTitle("Java 开发工程师");
        row.setPersonId(60001L);
        row.setPersonName("张三");
        row.setDeptId(20001L);
        row.setOwnerHrId(10001L);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 19, 12, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 10, 19, 12, 0));
        return row;
    }

    private static ScheduleConflictRow conflict() {
        ScheduleConflictRow conflict = new ScheduleConflictRow();
        conflict.setParticipantType("INTERVIEWER");
        conflict.setParticipantId(10002L);
        conflict.setSlotDate(LocalDate.of(2026, 10, 20));
        conflict.setSlotNo(18);
        conflict.setInterviewId(90002L);
        conflict.setStartTime(LocalDateTime.of(2026, 10, 20, 1, 0));
        conflict.setEndTime(LocalDateTime.of(2026, 10, 20, 2, 0));
        return conflict;
    }
}
