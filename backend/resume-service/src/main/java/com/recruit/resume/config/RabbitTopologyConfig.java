package com.recruit.resume.config;

import com.recruit.contracts.event.EventTypes;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑（与 A 文档 §6.1 路由约定对齐）。
 *
 * <pre>
 * exchange:   recruit.events (topic)
 * routingKey: eventType（如 resume.received / job.published）
 * queue:      {service}.{event-type}，如 mineru.resume-received、resume.job-published
 * DLQ:        {queue}.dlq，绑到 recruit.dlx
 * </pre>
 *
 * <p>B 在这里声明自己需要消费/投递的队列，避免“交换機存在但没有绑定，消息被静默丢弃”。
 * mineru-worker 启动后直接消费 {@code mineru.resume-received} 即可。</p>
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange eventsExchange(
            @Value("${recruit.mq.events-exchange:recruit.events}") String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    @Bean
    public TopicExchange deadLetterExchange(
            @Value("${recruit.mq.dlx:recruit.dlx}") String name) {
        return ExchangeBuilder.topicExchange(name).durable(true).build();
    }

    // ==================== resume.received → mineru-worker ====================

    @Bean
    public Queue mineruResumeQueue(
            @Value("${recruit.mq.mineru-resume-queue:mineru.resume-received}") String name,
            @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(dlx.getName())
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }

    @Bean
    public Queue mineruResumeDlq(
            @Value("${recruit.mq.mineru-resume-queue:mineru.resume-received}") String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    @Bean
    public Binding mineruResumeBinding(@Qualifier("mineruResumeQueue") Queue queue,
                                       @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(EventTypes.RESUME_RECEIVED);
    }

    @Bean
    public Binding mineruResumeDlqBinding(@Qualifier("mineruResumeDlq") Queue queue,
                                          @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return BindingBuilder.bind(queue).to(dlx).with(queue.getName());
    }

    // ==================== resume.parse.completed → 分块/Embedding/ES ====================

    @Bean
    public Queue resumeParseCompletedQueue(
            @Value("${recruit.mq.resume-parse-completed-queue:resume.parse-completed}") String name,
            @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(dlx.getName())
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }

    @Bean
    public Queue resumeParseCompletedDlq(
            @Value("${recruit.mq.resume-parse-completed-queue:resume.parse-completed}") String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    @Bean
    public Binding resumeParseCompletedBinding(
            @Qualifier("resumeParseCompletedQueue") Queue queue,
            @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(EventTypes.RESUME_PARSE_COMPLETED);
    }

    @Bean
    public Binding resumeParseCompletedDlqBinding(
            @Qualifier("resumeParseCompletedDlq") Queue queue,
            @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return BindingBuilder.bind(queue).to(dlx).with(queue.getName());
    }

    // ==================== job.published → resume.job-published ====================

    @Bean
    public Queue jobPublishedQueue(
            @Value("${recruit.mq.job-published-queue:resume.job-published}") String name,
            @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(dlx.getName())
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }

    @Bean
    public Queue jobPublishedDlq(
            @Value("${recruit.mq.job-published-queue:resume.job-published}") String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    @Bean
    public Binding jobPublishedBinding(@Qualifier("jobPublishedQueue") Queue queue,
                                       @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(EventTypes.JOB_PUBLISHED);
    }

    @Bean
    public Binding jobPublishedDlqBinding(@Qualifier("jobPublishedDlq") Queue queue,
                                          @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return BindingBuilder.bind(queue).to(dlx).with(queue.getName());
    }

    // ==================== job.updated → resume.job-updated ====================

    @Bean
    public Queue jobUpdatedQueue(
            @Value("${recruit.mq.job-updated-queue:resume.job-updated}") String name,
            @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(dlx.getName())
                .deadLetterRoutingKey(name + ".dlq")
                .build();
    }

    @Bean
    public Queue jobUpdatedDlq(
            @Value("${recruit.mq.job-updated-queue:resume.job-updated}") String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    @Bean
    public Binding jobUpdatedBinding(@Qualifier("jobUpdatedQueue") Queue queue,
                                     @Qualifier("eventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(EventTypes.JOB_UPDATED);
    }

    @Bean
    public Binding jobUpdatedDlqBinding(@Qualifier("jobUpdatedDlq") Queue queue,
                                        @Qualifier("deadLetterExchange") TopicExchange dlx) {
        return BindingBuilder.bind(queue).to(dlx).with(queue.getName());
    }
}
