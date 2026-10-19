package com.recruit.contracts.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 事件信封（开发文档 5.1）。字段只增不改；破坏性变更走 eventVersion 升级。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventEnvelope<T> implements Serializable {

    /** 全局唯一事件 ID（ULID/雪花字符串），幂等消费键之一 */
    private String eventId;
    /** 事件类型，如 interview.scheduled（见 EventTypes） */
    private String eventType;
    /** 事件 Schema 版本 */
    private Integer eventVersion;
    /** 聚合根 ID */
    private String aggregateId;
    /** 聚合根类型，如 job / application / room */
    private String aggregateType;
    /** 全链路追踪 ID */
    private String traceId;
    /** 发生时间（ISO 8601 / UTC） */
    private Instant occurredAt;
    /** 事件负载 */
    private T payload;
}
