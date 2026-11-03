package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code room.create.failed} 负载（room → interview）：记录失败原因，保持 ROOM_PENDING 供重试。
 *
 * @param interviewId 面试 ID
 * @param reason      失败原因
 * @param occurredAt  事件时间
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomCreateFailedPayload(
        String interviewId,
        String reason,
        Instant occurredAt) {
}
