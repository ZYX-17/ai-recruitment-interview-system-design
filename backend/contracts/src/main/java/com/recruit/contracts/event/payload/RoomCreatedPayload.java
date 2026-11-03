package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code room.created} 负载（room → interview）：回填房间号并置 SCHEDULED。
 *
 * @param interviewId 面试 ID
 * @param roomId      房间 ID
 * @param roomType    房间类型
 * @param occurredAt  事件时间
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomCreatedPayload(
        String interviewId,
        String roomId,
        String roomType,
        Instant occurredAt) {
}
