package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code talent.followup.due} 的负载（resume → notify）。
 *
 * @param talentId      人才 ID
 * @param personId      候选人 ID
 * @param hrId          负责 HR 用户 ID（接收人）
 * @param nextFollowupAt 下次回访时间
 * @param candidateName 候选人姓名（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TalentFollowupDuePayload(
        String talentId,
        String personId,
        String hrId,
        String nextFollowupAt,
        String candidateName) {
}
