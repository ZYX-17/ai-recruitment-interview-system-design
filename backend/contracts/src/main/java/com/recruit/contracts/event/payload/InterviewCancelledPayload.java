package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code interview.cancelled} 的负载（room 关房、notify 发取消通知）。
 *
 * @param interviewId   面试 ID
 * @param applicationId 投递 ID
 * @param roomId        房间 ID，可为空
 * @param reason        取消原因
 * @param cancelledAt   取消时间（UTC）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewCancelledPayload(
        String interviewId,
        String applicationId,
        String roomId,
        String reason,
        Instant cancelledAt) {
}
