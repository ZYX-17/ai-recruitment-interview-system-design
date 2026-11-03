package com.recruit.interview.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 投递器：publisher confirm 成功才算投递成功。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitOutboxSender implements OutboxSender {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;

    @Override
    public String send(String exchange, String routingKey, String payload) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (confirm.isAck()) {
                return null;
            }
            return confirm.getReason() == null ? "broker nack" : confirm.getReason();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "投递确认被中断";
        } catch (Exception e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }
}
