package com.recruit.notify.config;

import com.recruit.contracts.event.EventRouting;
import com.recruit.contracts.event.EventTypes;
import org.springframework.stereotype.Component;

/**
 * notify 消费队列名（全部由 {@link EventRouting#queue} 计算，不手写。）
 */
@Component
public class NotifyQueues {

    private final String notificationRequested =
            EventRouting.queue("notify", EventTypes.NOTIFICATION_REQUESTED);

    private final String resumeParseFailed =
            EventRouting.queue("notify", EventTypes.RESUME_PARSE_FAILED);

    private final String candidateDuplicated =
            EventRouting.queue("notify", EventTypes.CANDIDATE_DUPLICATED);

    private final String talentFollowupDue =
            EventRouting.queue("notify", EventTypes.TALENT_FOLLOWUP_DUE);

    public String getNotificationRequested() {
        return notificationRequested;
    }

    public String getResumeParseFailed() {
        return resumeParseFailed;
    }

    public String getCandidateDuplicated() {
        return candidateDuplicated;
    }

    public String getTalentFollowupDue() {
        return talentFollowupDue;
    }
}
