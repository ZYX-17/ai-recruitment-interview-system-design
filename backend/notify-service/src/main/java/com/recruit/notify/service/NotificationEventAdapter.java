package com.recruit.notify.service;

import com.recruit.contracts.event.payload.CandidateDuplicatedPayload;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.contracts.event.payload.ResumeParseFailedPayload;
import com.recruit.contracts.event.payload.TalentFollowupDuePayload;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 resume 域业务事件转成统一的 {@code notification.requested} 语义。
 */
@Component
public class NotificationEventAdapter {

    /**
     * 简历解析失败 → HR 站内信。
     *
     * @param payload 事件负载
     * @return 通知请求；缺接收人时返回 null
     */
    public NotificationRequestedPayload fromResumeParseFailed(ResumeParseFailedPayload payload) {
        if (payload == null || isBlank(payload.receiverId())) {
            return null;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("resumeId", valueOr(payload.resumeId(), "-"));
        variables.put("attempt", payload.attempt() == null ? 1 : payload.attempt());
        variables.put("reason", valueOr(payload.reason(), "未知原因"));
        return new NotificationRequestedPayload("resume_parse_failed", payload.receiverId(), null,
                List.of("INAPP"), "RESUME_PARSE_FAILED", valueOr(payload.resumeId(), "-"), variables,
                "resume_parse_failed:" + payload.resumeId() + ":" + payload.attempt());
    }

    /**
     * 候选人查重 → HR 站内信。
     *
     * @param payload 事件负载
     * @return 通知请求；缺接收人时返回 null
     */
    public NotificationRequestedPayload fromCandidateDuplicated(CandidateDuplicatedPayload payload) {
        if (payload == null || isBlank(payload.receiverId())) {
            return null;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("personId", valueOr(payload.personId(), "-"));
        variables.put("duplicatePersonIds", payload.duplicatePersonIds() == null
                ? "-" : String.join(",", payload.duplicatePersonIds()));
        variables.put("similarity", payload.similarity() == null ? "-" : payload.similarity());
        variables.put("dedupType", valueOr(payload.dedupType(), "-"));
        return new NotificationRequestedPayload("candidate_duplicate", payload.receiverId(), null,
                List.of("INAPP"), "CANDIDATE_DUPLICATED", valueOr(payload.personId(), "-"), variables,
                "candidate_duplicate:" + payload.personId() + ":" + payload.similarity());
    }

    /**
     * 人才回访到期 → 负责 HR 站内信。
     *
     * @param payload 事件负载
     * @return 通知请求；缺接收人时返回 null
     */
    public NotificationRequestedPayload fromTalentFollowupDue(TalentFollowupDuePayload payload) {
        if (payload == null || isBlank(payload.hrId())) {
            return null;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("candidateName", isBlank(payload.candidateName())
                ? valueOr(payload.personId(), "-") : payload.candidateName());
        variables.put("nextFollowupAt", valueOr(payload.nextFollowupAt(), "-"));
        return new NotificationRequestedPayload("followup_due", payload.hrId(), null,
                List.of("INAPP"), "TALENT_FOLLOWUP_DUE", valueOr(payload.talentId(), "-"), variables,
                "followup_due:" + payload.talentId() + ":" + payload.nextFollowupAt());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String valueOr(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
