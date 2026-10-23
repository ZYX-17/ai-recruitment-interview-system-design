package com.recruit.resume.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.recruit.resume.entity.LocalMessage;
import com.recruit.resume.mapper.LocalMessageMapper;
import com.recruit.resume.support.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Outbox 发布器：每 5 秒扫描 PENDING/FAILED 且到期的消息，投递到 {@code recruit.events}。
 *
 * <p>投递结果由 publisher confirm 回调写回（见 {@link OutboxConfirmCallback}）；
 * 发送阶段直接抛异常（如 broker 不可用）时在这里标记失败并退避。</p>
 *
 * <p>{@code MQ_ENABLED=false} 时只保留 Outbox，不投递，主业务流程不受影响。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int MAX_BATCH = 500;

    private final LocalMessageMapper localMessageMapper;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxStateService outboxStateService;

    @Value("${recruit.mq.enabled:true}")
    private boolean mqEnabled;

    @Value("${recruit.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${recruit.outbox.poll-interval-ms:5000}")
    public void publishPending() {
        if (!mqEnabled) {
            return;
        }
        int limit = Math.min(Math.max(batchSize, 1), MAX_BATCH);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<LocalMessage> messages = localMessageMapper.selectList(Wrappers.<LocalMessage>lambdaQuery()
                .in(LocalMessage::getStatus, OutboxStatus.PENDING, OutboxStatus.FAILED)
                .and(w -> w.isNull(LocalMessage::getNextRetryAt)
                        .or()
                        .le(LocalMessage::getNextRetryAt, now))
                .orderByAsc(LocalMessage::getId)
                .last("LIMIT " + limit));

        for (LocalMessage message : messages) {
            send(message);
        }
    }

    private void send(LocalMessage message) {
        try {
            CorrelationData correlationData = new CorrelationData(message.getMessageId());
            rabbitTemplate.convertAndSend(message.getExchange(), message.getRoutingKey(), message.getPayload(),
                    amqpMessage -> {
                        amqpMessage.getMessageProperties()
                                .setContentType(MediaType.APPLICATION_JSON_VALUE);
                        amqpMessage.getMessageProperties().setMessageId(message.getMessageId());
                        if (StringUtils.hasText(message.getTraceId())) {
                            amqpMessage.getMessageProperties().setHeader("X-Trace-Id", message.getTraceId());
                        }
                        return amqpMessage;
                    },
                    correlationData);
        } catch (Exception e) {
            log.warn("Outbox 投递异常 messageId={}, eventType={}",
                    message.getMessageId(), message.getEventType(), e);
            outboxStateService.markFailed(message.getMessageId(), e.getMessage());
        }
    }
}
