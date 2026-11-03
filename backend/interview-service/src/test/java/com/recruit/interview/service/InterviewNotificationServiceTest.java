package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.row.InterviewRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 面试邀约/改期/取消通知（P10-6）。
 */
@ExtendWith(MockitoExtension.class)
class InterviewNotificationServiceTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    private InterviewNotificationService service;

    @BeforeEach
    void setUp() {
        service = new InterviewNotificationService(outboxPublisher, new ObjectMapper(),
                "http://localhost:5173/room/");
    }

    @Test
    void invitePublishesCandidateAndInterviewersWithRoomLink() {
        InterviewRow row = row(70001L);

        service.publishInvite(row);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher, times(3)).publish(eq(EventTypes.NOTIFICATION_REQUESTED),
                eq("90001"), eq("interview"), captor.capture());
        List<Object> payloads = captor.getAllValues();

        NotificationRequestedPayload candidate = (NotificationRequestedPayload) payloads.get(0);
        assertThat(candidate.receiverId()).isEqualTo("60001");
        assertThat(candidate.channel()).containsExactly("EMAIL", "INAPP");
        assertThat(candidate.templateCode()).isEqualTo("interview_invite");
        assertThat(candidate.bizType()).isEqualTo(InterviewNotificationService.BIZ_INVITE);
        assertThat(candidate.dedupKey()).isEqualTo("interview_invite:90001:60001");
        assertThat(candidate.variables())
                .containsEntry("joinUrl", "http://localhost:5173/room/70001")
                .containsEntry("startTime", "2026-10-20 09:00")
                .containsEntry("mode", "线上")
                .containsEntry("candidateName", "张三");

        NotificationRequestedPayload firstInterviewer = (NotificationRequestedPayload) payloads.get(1);
        assertThat(firstInterviewer.receiverId()).isEqualTo("10002");
        assertThat(firstInterviewer.channel()).containsExactly("INAPP");
        assertThat(firstInterviewer.dedupKey()).isEqualTo("interview_invite:90001:10002");
        NotificationRequestedPayload secondInterviewer = (NotificationRequestedPayload) payloads.get(2);
        assertThat(secondInterviewer.receiverId()).isEqualTo("10003");
    }

    @Test
    void inviteSkippedWhenRoomMissing() {
        service.publishInvite(row(null));

        verify(outboxPublisher, never()).publish(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rescheduleCarriesOldAndNewTime() {
        InterviewRow row = row(70001L);

        service.publishReschedule(row, LocalDateTime.of(2026, 10, 20, 1, 0),
                LocalDateTime.of(2026, 10, 21, 2, 0));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher, times(3)).publish(eq(EventTypes.NOTIFICATION_REQUESTED),
                eq("90001"), eq("interview"), captor.capture());
        NotificationRequestedPayload candidate =
                (NotificationRequestedPayload) captor.getAllValues().get(0);
        assertThat(candidate.templateCode()).isEqualTo("interview_reschedule");
        assertThat(candidate.variables())
                .containsEntry("oldStartTime", "2026-10-20 09:00")
                .containsEntry("newStartTime", "2026-10-21 10:00")
                .containsEntry("joinUrl", "http://localhost:5173/room/70001");
        assertThat(candidate.dedupKey())
                .startsWith("interview_reschedule:90001:60001:")
                .endsWith(String.valueOf(LocalDateTime.of(2026, 10, 21, 2, 0)
                        .toInstant(java.time.ZoneOffset.UTC).getEpochSecond()));
    }

    @Test
    void rescheduleSkippedWhenRoomMissing() {
        service.publishReschedule(row(null), LocalDateTime.of(2026, 10, 20, 1, 0),
                LocalDateTime.of(2026, 10, 21, 2, 0));

        verify(outboxPublisher, never()).publish(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelledFallsBackToDefaultReason() {
        service.publishCancelled(row(70001L), "  ");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher, times(3)).publish(eq(EventTypes.NOTIFICATION_REQUESTED),
                eq("90001"), eq("interview"), captor.capture());
        NotificationRequestedPayload candidate =
                (NotificationRequestedPayload) captor.getAllValues().get(0);
        assertThat(candidate.templateCode()).isEqualTo("interview_cancelled");
        assertThat(candidate.variables()).containsEntry("reason", "组织安排调整");
        assertThat(candidate.dedupKey()).isEqualTo("interview_cancelled:90001:60001");
    }

    private static InterviewRow row(Long roomId) {
        InterviewRow row = new InterviewRow();
        row.setId(90001L);
        row.setApplicationId(51001L);
        row.setRoundNo(1);
        row.setInterviewerIds("[\"10002\",\"10003\"]");
        row.setStartTime(LocalDateTime.of(2026, 10, 20, 1, 0));
        row.setEndTime(LocalDateTime.of(2026, 10, 20, 2, 0));
        row.setMode("ONLINE");
        row.setRoomType("ONE_TO_ONE");
        row.setRoomId(roomId);
        row.setStatus("SCHEDULED");
        row.setJobId(50001L);
        row.setJobTitle("Java 开发工程师");
        row.setPersonId(60001L);
        row.setPersonName("张三");
        return row;
    }
}
