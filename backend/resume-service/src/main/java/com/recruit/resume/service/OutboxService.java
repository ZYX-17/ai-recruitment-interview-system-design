package com.recruit.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.id.IdWorker;
import com.recruit.common.trace.TraceIdUtil;
import com.recruit.contracts.event.EventEnvelope;
import com.recruit.resume.entity.LocalMessage;
import com.recruit.resume.mapper.LocalMessageMapper;
import com.recruit.resume.support.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox 写入器：业务表与 local_message 必须在同一事务中落库。
 *
 * <p>这里只负责“记下来”，真正的投递由后台发布器扫描 PENDING 完成；
 * 这样即使 RabbitMQ 暂时不可用，创建简历等主流程也不会失败。</p>
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final LocalMessageMapper localMessageMapper;
    private final IdWorker idWorker;
    private final ObjectMapper objectMapper;

    /** 交换机名；B 域默认 recruit.resume，可由 Nacos/环境变量覆盖。 */
    @Value("${recruit.outbox.exchange:recruit.resume}")
    private String exchange;

    /**
     * 追加一条待投递事件。
     *
     * @param eventType     事件类型，见 EventTypes
     * @param aggregateId   聚合根 ID（字符串）
     * @param aggregateType 聚合根类型，如 RESUME
     * @param payload       事件载荷（会被序列化进 EventEnvelope）
     */
    public void append(String eventType, String aggregateId, String aggregateType, Object payload) {
        String eventId = UUID.randomUUID().toString();
        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .eventVersion(1)
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .traceId(TraceIdUtil.get())
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        LocalMessage message = new LocalMessage();
        message.setId(idWorker.nextId());
        message.setMessageId(eventId);
        message.setEventType(eventType);
        message.setEventVersion(1);
        message.setAggregateId(aggregateId);
        message.setTraceId(envelope.getTraceId());
        message.setExchange(exchange);
        message.setRoutingKey(eventType);
        message.setPayload(toJson(envelope));
        message.setStatus(OutboxStatus.PENDING);
        message.setRetryCount(0);
        localMessageMapper.insert(message);
    }

    private String toJson(EventEnvelope<Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // 信封只含字符串/时间/Map，正常不会失败；失败说明代码有 bug，直接中断事务
            throw new IllegalStateException("事件信封序列化失败", e);
        }
    }
}
