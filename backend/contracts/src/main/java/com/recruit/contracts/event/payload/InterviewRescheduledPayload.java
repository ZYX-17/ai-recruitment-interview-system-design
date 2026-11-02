package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code interview.rescheduled} 的负载（room 更新房间时间、notify 重发邀约）。
 *
 * <p>消费方应按 interviewId 幂等更新，不要用“旧时间”作为匹配条件。</p>
 *
 * @param interviewId   面试 ID
 * @param applicationId 投递 ID
 * @param oldStartTime  原开始时间（UTC）
 * @param newStartTime  新开始时间（UTC）
 * @param roomId        房间 ID，可为空
 * @param rescheduledAt 改期操作时间（UTC）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewRescheduledPayload(
        String interviewId,
        String applicationId,
        Instant oldStartTime,
        Instant newStartTime,
        String roomId,
        Instant rescheduledAt) {
}
