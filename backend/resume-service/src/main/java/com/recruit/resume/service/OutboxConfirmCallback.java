package com.recruit.resume.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RabbitMQ publisher confirm 回调：ack → SENT，nack → 退避重试。
 *
 * <p>CorrelationData 的 id 就是 local_message.message_id，因此能把 broker 的确认
 * 精确写回对应 Outbox 行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxConfirmCallback implements RabbitTemplate.ConfirmCallback {

    private final OutboxStateService outboxStateService;

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        String messageId = correlationData == null ? null : correlationData.getId();
        if (!StringUtils.hasText(messageId)) {
            log.warn("收到没有 CorrelationData 的 publisher confirm，忽略");
            return;
        }
        if (ack) {
            outboxStateService.markSent(messageId);
        } else {
            outboxStateService.markFailed(messageId, cause);
        }
    }
}
