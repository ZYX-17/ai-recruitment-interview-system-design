package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code room.finished} 负载（room → interview/ai）：房间结束，面试置 COMPLETED。
 *
 * @param interviewId 面试 ID
 * @param roomId      房间 ID
 * @param endedAt     结束时间
 * @param durationSec 时长（秒）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoomFinishedPayload(
        String interviewId,
        String roomId,
        Instant endedAt,
        Integer durationSec) {
}
