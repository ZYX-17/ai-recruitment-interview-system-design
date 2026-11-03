package com.recruit.interview.dto;

import java.time.Instant;

/**
 * Outbox 消息视图（管理页）。
 *
 * @param id           消息主键
 * @param messageId    事件 ID（重投不变）
 * @param eventType    事件类型
 * @param eventVersion 事件版本
 * @param aggregateId  聚合根 ID
 * @param status       PENDING/SENT/CONFIRMED/FAILED/DEAD
 * @param retryCount   已重试次数
 * @param nextRetryAt  下次重试时间（UTC）
 * @param createdAt    创建时间（UTC）
 * @param updatedAt    更新时间（UTC）
 * @param version      乐观锁版本
 */
public record OutboxMessageView(
        String id,
        String messageId,
        String eventType,
        Integer eventVersion,
        String aggregateId,
        String status,
        Integer retryCount,
        Instant nextRetryAt,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {
}
