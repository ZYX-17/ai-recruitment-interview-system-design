package com.recruit.resume.config;

import com.recruit.resume.service.OutboxConfirmCallback;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

/**
 * 给 Spring Boot 自动配置的 RabbitTemplate 挂上确认回调与 mandatory 退回日志。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitConfirmConfig {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxConfirmCallback outboxConfirmCallback;

    @PostConstruct
    public void configure() {
        rabbitTemplate.setConfirmCallback(outboxConfirmCallback);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "事件无法路由，exchange={}, routingKey={}, replyText={}, messageId={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyText(),
                returned.getMessage().getMessageProperties().getMessageId()));
    }
}
