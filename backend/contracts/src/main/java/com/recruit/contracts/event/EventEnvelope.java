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

    /**
     * 创建一个事件信封：自动填 eventVersion（查 {@link EventSchemaVersion}）和 occurredAt。
     *
     * <p>业务代码只关心 eventId/type/aggregate/payload，版本和时间统一在这里生成，
     * 避免不同服务写出不同的 eventVersion。</p>
     *
     * @param eventId       全局唯一事件 ID（重投时保持不变）
     * @param eventType     事件类型
     * @param aggregateId   聚合根 ID
     * @param aggregateType 聚合根类型，例如 interview
     * @param traceId       链路 ID
     * @param payload       事件负载
     * @param <T>           负载类型
     * @return 事件信封
     * @throws IllegalArgumentException 事件类型未登记时抛出
     */
    public static <T> EventEnvelope<T> of(String eventId, String eventType, String aggregateId,
                                          String aggregateType, String traceId, T payload) {
        return EventEnvelope.<T>builder()
                .eventId(eventId)
                .eventType(eventType)
                .eventVersion(EventSchemaVersion.current(eventType))
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .traceId(traceId)
                .occurredAt(Instant.now())
                .payload(payload)
                .build();
    }
}
