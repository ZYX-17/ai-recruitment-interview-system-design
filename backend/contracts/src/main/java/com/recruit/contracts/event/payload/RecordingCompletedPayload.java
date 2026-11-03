package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code recording.completed} 负载（room → interview/ai）：录像登记。
 *
 * @param interviewId 面试 ID
 * @param roomId      房间 ID
 * @param recordId    录像 ID（幂等键）
 * @param fileKey     file-service/MinIO 对象键
 * @param durationSec 时长（秒）
 * @param occurredAt  事件时间
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordingCompletedPayload(
        String interviewId,
        String roomId,
        String recordId,
        String fileKey,
        Integer durationSec,
        Instant occurredAt) {
}
