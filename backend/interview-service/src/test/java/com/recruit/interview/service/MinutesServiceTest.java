package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.MinutesReviewForm;
import com.recruit.interview.dto.MinutesView;
import com.recruit.interview.dto.MinutesWriteForm;
import com.recruit.interview.entity.Interview;
import com.recruit.interview.entity.InterviewMinutes;
import com.recruit.interview.mapper.InterviewMapper;
import com.recruit.interview.mapper.InterviewMinutesMapper;
import com.recruit.interview.mapper.row.InterviewRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纪要回写/查询/确认（P10-4、P10-8）。
 */
@ExtendWith(MockitoExtension.class)
class MinutesServiceTest {

    @Mock
    private InterviewMapper interviewMapper;
    @Mock
    private InterviewMinutesMapper minutesMapper;
    @Mock
    private DataScopeService dataScopeService;

    private MinutesService service;

    @BeforeEach
    void setUp() {
        service = new MinutesService(interviewMapper, minutesMapper, dataScopeService,
                new ObjectMapper());
        UserContext.set("10001", List.of("HR_ADMIN"), "HR", "trace");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void writeCreatesPendingMinutes() {
        when(interviewMapper.selectById(91001L)).thenReturn(new Interview());
        when(minutesMapper.selectByInterviewId(91001L)).thenReturn(null,
                minutes(95001L, "PENDING", 0, "task-1"));
        when(minutesMapper.insert(any(InterviewMinutes.class))).thenAnswer(invocation -> {
            InterviewMinutes row = invocation.getArgument(0);
            row.setId(95001L);
            return 1;
        });

        MinutesView view = service.write(91001L, writeForm(3));

        ArgumentCaptor<InterviewMinutes> captor = ArgumentCaptor.forClass(InterviewMinutes.class);
        verify(minutesMapper).insert(captor.capture());
        assertThat(captor.getValue().getReviewStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getAiTaskId()).isEqualTo("task-1");
        assertThat(view.reviewStatus()).isEqualTo("PENDING");
    }

    @Test
    void writeMarksInsufficientTranscriptWhenNoSegments() {
        when(interviewMapper.selectById(91001L)).thenReturn(new Interview());
        when(minutesMapper.selectByInterviewId(91001L)).thenReturn(null,
                minutes(95001L, "INSUFFICIENT_TRANSCRIPT", 0, "task-2"));
        when(minutesMapper.insert(any(InterviewMinutes.class))).thenAnswer(invocation -> {
            InterviewMinutes row = invocation.getArgument(0);
            row.setId(95001L);
            return 1;
        });

        assertThat(service.write(91001L, writeForm(0)).reviewStatus())
                .isEqualTo("INSUFFICIENT_TRANSCRIPT");
    }

    @Test
    void writeIsIdempotentByTaskId() {
        when(interviewMapper.selectById(91001L)).thenReturn(new Interview());
        when(minutesMapper.selectByInterviewId(91001L))
                .thenReturn(minutes(95001L, "PENDING", 2, "task-1"));

        MinutesView view = service.write(91001L, writeForm(2));

        assertThat(view.aiTaskId()).isEqualTo("task-1");
        verify(minutesMapper, never()).updateFromAi(anyLong(), anyString(), any(), anyInt(),
                any(), any(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void writeRejectsOverwriteConfirmedMinutes() {
        when(interviewMapper.selectById(91001L)).thenReturn(new Interview());
        when(minutesMapper.selectByInterviewId(91001L))
                .thenReturn(minutes(95001L, "CONFIRMED", 2, "task-old"));

        assertThatThrownBy(() -> service.write(91001L, writeForm(2)))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ILLEGAL_TRANSITION);
    }

    @Test
    void detailRejectsUserOutsideScopeAndRoster() {
        UserContext.set("10001", List.of(), "HR", "trace");
        InterviewRow row = interviewRow();
        row.setOwnerHrId(20002L);
        row.setInterviewerIds("[\"20002\"]");
        when(interviewMapper.selectRowById(91001L)).thenReturn(row);
        when(dataScopeService.current()).thenReturn(
                new DataScopeService.DataScope("SELF", 20001L, List.of(), 10001L));

        assertThatThrownBy(() -> service.detail(91001L))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    void detailAllowsAssignedInterviewer() {
        UserContext.set("20002", List.of(), "面试官", "trace");
        InterviewRow row = interviewRow();
        row.setOwnerHrId(30003L);
        row.setInterviewerIds("[\"20002\"]");
        when(interviewMapper.selectRowById(91001L)).thenReturn(row);
        when(dataScopeService.current()).thenReturn(
                new DataScopeService.DataScope("SELF", 20001L, List.of(), 20002L));
        when(minutesMapper.selectByInterviewId(91001L))
                .thenReturn(minutes(95001L, "PENDING", 2, "task-1"));

        assertThat(service.detail(91001L).reviewStatus()).isEqualTo("PENDING");
    }

    @Test
    void reviewConfirmsMinutes() {
        when(minutesMapper.selectById(95001L))
                .thenReturn(minutes(95001L, "PENDING", 0, "task-1"),
                        minutes(95001L, "CONFIRMED", 1, "task-1"));
        when(interviewMapper.selectRowById(91001L)).thenReturn(interviewRow());
        when(dataScopeService.current()).thenReturn(
                new DataScopeService.DataScope("ALL", null, List.of(), 10001L));
        when(minutesMapper.review(eq(95001L), eq("CONFIRMED"), any(), eq(10001L),
                any(LocalDateTime.class), eq(0))).thenReturn(1);

        MinutesReviewForm form = new MinutesReviewForm();
        form.setDecision("CONFIRM");
        form.setVersion(0);

        assertThat(service.review(95001L, form).reviewStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void reviewIsIdempotentWhenAlreadyConfirmed() {
        when(minutesMapper.selectById(95001L))
                .thenReturn(minutes(95001L, "CONFIRMED", 1, "task-1"));
        when(interviewMapper.selectRowById(91001L)).thenReturn(interviewRow());
        when(dataScopeService.current()).thenReturn(
                new DataScopeService.DataScope("ALL", null, List.of(), 10001L));

        MinutesReviewForm form = new MinutesReviewForm();
        form.setDecision("CONFIRM");
        form.setVersion(0);

        assertThat(service.review(95001L, form).reviewStatus()).isEqualTo("CONFIRMED");
        verify(minutesMapper, never()).review(anyLong(), anyString(), any(), anyLong(),
                any(LocalDateTime.class), anyInt());
    }

    private static MinutesWriteForm writeForm(int segments) {
        MinutesWriteForm form = new MinutesWriteForm();
        form.setTaskId("task-1");
        form.setTranscriptObjectKey("minutes/91001.json");
        form.setTranscriptSegmentCount(segments);
        form.setMinutesMd("# 纪要");
        form.setAiEvaluation(Map.of("risks", List.of("待确认")));
        form.setModel("mock-llm");
        form.setPromptVersion(1);
        return form;
    }

    private static InterviewMinutes minutes(long id, String status, int version, String taskId) {
        InterviewMinutes row = new InterviewMinutes();
        row.setId(id);
        row.setInterviewId(91001L);
        row.setAiTaskId(taskId);
        row.setTranscriptSegmentCount(2);
        row.setReviewStatus(status);
        row.setVersion(version);
        return row;
    }

    private static InterviewRow interviewRow() {
        InterviewRow row = new InterviewRow();
        row.setId(91001L);
        row.setDeptId(20001L);
        row.setOwnerHrId(10001L);
        row.setInterviewerIds("[\"10001\"]");
        return row;
    }
}
