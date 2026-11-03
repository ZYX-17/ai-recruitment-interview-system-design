package com.recruit.notify.service;

import com.recruit.contracts.event.payload.CandidateDuplicatedPayload;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.contracts.event.payload.ResumeParseFailedPayload;
import com.recruit.contracts.event.payload.TalentFollowupDuePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * resume 业务事件到通知请求的映射单测。
 */
class NotificationEventAdapterTest {

    private final NotificationEventAdapter adapter = new NotificationEventAdapter();

    @Test
    void resumeParseFailedMapsToInappNotification() {
        NotificationRequestedPayload request = adapter.fromResumeParseFailed(new ResumeParseFailedPayload(
                "R1", "T1", "PDF 损坏", 2, "60001"));

        assertThat(request.templateCode()).isEqualTo("resume_parse_failed");
        assertThat(request.channel()).containsExactly("INAPP");
        assertThat(request.receiverId()).isEqualTo("60001");
        assertThat(request.variables()).containsEntry("resumeId", "R1")
                .containsEntry("attempt", 2).containsEntry("reason", "PDF 损坏");
        assertThat(request.dedupKey()).isEqualTo("resume_parse_failed:R1:2");
    }

    @Test
    void resumeParseFailedWithoutReceiverIsSkipped() {
        assertThat(adapter.fromResumeParseFailed(new ResumeParseFailedPayload(
                "R1", "T1", "x", 1, null))).isNull();
    }

    @Test
    void candidateDuplicatedJoinsIds() {
        NotificationRequestedPayload request = adapter.fromCandidateDuplicated(new CandidateDuplicatedPayload(
                "P1", List.of("P2", "P3"), 0.92, "NAME_PHONE", "60001"));

        assertThat(request.templateCode()).isEqualTo("candidate_duplicate");
        assertThat(request.variables()).containsEntry("duplicatePersonIds", "P2,P3")
                .containsEntry("similarity", 0.92);
    }

    @Test
    void followupDueUsesHrAsReceiverAndFallsBackToPersonId() {
        NotificationRequestedPayload request = adapter.fromTalentFollowupDue(new TalentFollowupDuePayload(
                "TAL1", "P1", "60001", "2026-11-10 09:00", null));

        assertThat(request.templateCode()).isEqualTo("followup_due");
        assertThat(request.receiverId()).isEqualTo("60001");
        assertThat(request.variables()).containsEntry("candidateName", "P1")
                .containsEntry("nextFollowupAt", "2026-11-10 09:00");
    }

    @Test
    void followupDueWithoutHrIsSkipped() {
        assertThat(adapter.fromTalentFollowupDue(new TalentFollowupDuePayload(
                "TAL1", "P1", null, "2026-11-10 09:00", null))).isNull();
    }
}
