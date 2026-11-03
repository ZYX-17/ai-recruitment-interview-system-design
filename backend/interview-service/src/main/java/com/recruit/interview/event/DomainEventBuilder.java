package com.recruit.interview.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.trace.TraceIdUtil;
import com.recruit.contracts.event.EventEnvelope;
import com.recruit.contracts.event.EventRouting;
import com.recruit.contracts.event.EventSchemaVersion;
import com.recruit.interview.entity.LocalMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 构造 Outbox 消息：完整事件信封（含 eventId/traceId）一次落库，重投不重新生成 eventId。
 */
@Component
@RequiredArgsConstructor
public class DomainEventBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 构造待投递的本地消息。
     *
     * @param eventType     事件类型（EventTypes 常量）
     * @param aggregateId   聚合根 ID
     * @param aggregateType 聚合根类型（job/application/interview/...）
     * @param payload       业务负载
     * @return 未持久化的 {@link LocalMessage}
     */
    public LocalMessage build(String eventType, String aggregateId, String aggregateType, Object payload) {
        String eventId = UUID.randomUUID().toString().replace("-", "");
        EventEnvelope<Object> envelope = EventEnvelope.of(eventId, eventType, aggregateId,
                aggregateType, TraceIdUtil.get(), payload);
        LocalDateTime now = LocalDateTime.now();
        LocalMessage message = new LocalMessage();
        message.setMessageId(eventId);
        message.setEventType(eventType);
        message.setEventVersion(EventSchemaVersion.current(eventType));
        message.setAggregateId(aggregateId);
        message.setTraceId(envelope.getTraceId());
        message.setExchange(EventRouting.EXCHANGE);
        message.setRoutingKey(EventRouting.routingKey(eventType));
        message.setPayload(serialize(envelope));
        message.setStatus("PENDING");
        message.setRetryCount(0);
        message.setNextRetryAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        message.setVersion(0);
        return message;
    }

    private String serialize(EventEnvelope<Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("事件信封序列化失败: " + envelope.getEventType(), e);
        }
    }
}
