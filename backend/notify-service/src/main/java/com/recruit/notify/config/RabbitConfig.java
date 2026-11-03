package com.recruit.notify.config;

import com.recruit.contracts.event.EventRouting;
import com.recruit.contracts.event.EventTypes;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：四个消费队列 + 各自的 DLQ，手动 ack。
 *
 * <p>queue 名全部由 {@link EventRouting} 计算；失败消息拒绝后路由到
 * {@code {queue}.dlq}，便于人工排查与重放。</p>
 */
@Configuration
public class RabbitConfig {

    /**
     * 业务事件 topic exchange。
     */
    @Bean
    public TopicExchange recruitEventsExchange() {
        return new TopicExchange(EventRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationRequestedQueue(NotifyQueues queues) {
        return withDlq(queues.getNotificationRequested());
    }

    @Bean
    public Queue notificationRequestedDlq(NotifyQueues queues) {
        return deadLetter(queues.getNotificationRequested());
    }

    @Bean
    public Binding notificationRequestedBinding(Queue notificationRequestedQueue,
                                                TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(notificationRequestedQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.NOTIFICATION_REQUESTED));
    }

    @Bean
    public Queue resumeParseFailedQueue(NotifyQueues queues) {
        return withDlq(queues.getResumeParseFailed());
    }

    @Bean
    public Queue resumeParseFailedDlq(NotifyQueues queues) {
        return deadLetter(queues.getResumeParseFailed());
    }

    @Bean
    public Binding resumeParseFailedBinding(Queue resumeParseFailedQueue,
                                            TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(resumeParseFailedQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.RESUME_PARSE_FAILED));
    }

    @Bean
    public Queue candidateDuplicatedQueue(NotifyQueues queues) {
        return withDlq(queues.getCandidateDuplicated());
    }

    @Bean
    public Queue candidateDuplicatedDlq(NotifyQueues queues) {
        return deadLetter(queues.getCandidateDuplicated());
    }

    @Bean
    public Binding candidateDuplicatedBinding(Queue candidateDuplicatedQueue,
                                              TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(candidateDuplicatedQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.CANDIDATE_DUPLICATED));
    }

    @Bean
    public Queue talentFollowupDueQueue(NotifyQueues queues) {
        return withDlq(queues.getTalentFollowupDue());
    }

    @Bean
    public Queue talentFollowupDueDlq(NotifyQueues queues) {
        return deadLetter(queues.getTalentFollowupDue());
    }

    @Bean
    public Binding talentFollowupDueBinding(Queue talentFollowupDueQueue,
                                            TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(talentFollowupDueQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.TALENT_FOLLOWUP_DUE));
    }

    /**
     * 手动 ack 的监听容器。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        return factory;
    }

    private static Queue withDlq(String queueName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", EventRouting.dlq(queueName))
                .build();
    }

    private static Queue deadLetter(String queueName) {
        return QueueBuilder.durable(EventRouting.dlq(queueName)).build();
    }
}
