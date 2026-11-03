package com.recruit.interview.event;

/**
 * Outbox 投递通道（便于替换为 RabbitMQ 或测试桩）。
 */
public interface OutboxSender {

    /**
     * 投递一条消息并等待 broker 确认。
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param payload    完整事件信封 JSON
     * @return {@code null} 表示确认成功；否则为失败原因
     */
    String send(String exchange, String routingKey, String payload);
}
