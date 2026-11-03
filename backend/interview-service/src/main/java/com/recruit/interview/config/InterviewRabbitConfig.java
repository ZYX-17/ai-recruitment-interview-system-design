package com.recruit.interview.config;

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
 * room 事件消费拓扑：四个队列 + 各自 DLQ，手动 ack。
 */
@Configuration
public class InterviewRabbitConfig {

    @Bean
    public TopicExchange recruitEventsExchange() {
        return new TopicExchange(EventRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue interviewRoomCreatedQueue(InterviewQueues queues) {
        return withDlq(queues.getRoomCreated());
    }

    @Bean
    public Queue interviewRoomCreatedDlq(InterviewQueues queues) {
        return deadLetter(queues.getRoomCreated());
    }

    @Bean
    public Binding interviewRoomCreatedBinding(Queue interviewRoomCreatedQueue,
                                               TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(interviewRoomCreatedQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.ROOM_CREATED));
    }

    @Bean
    public Queue interviewRoomCreateFailedQueue(InterviewQueues queues) {
        return withDlq(queues.getRoomCreateFailed());
    }

    @Bean
    public Queue interviewRoomCreateFailedDlq(InterviewQueues queues) {
        return deadLetter(queues.getRoomCreateFailed());
    }

    @Bean
    public Binding interviewRoomCreateFailedBinding(Queue interviewRoomCreateFailedQueue,
                                                    TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(interviewRoomCreateFailedQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.ROOM_CREATE_FAILED));
    }

    @Bean
    public Queue interviewRoomFinishedQueue(InterviewQueues queues) {
        return withDlq(queues.getRoomFinished());
    }

    @Bean
    public Queue interviewRoomFinishedDlq(InterviewQueues queues) {
        return deadLetter(queues.getRoomFinished());
    }

    @Bean
    public Binding interviewRoomFinishedBinding(Queue interviewRoomFinishedQueue,
                                                TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(interviewRoomFinishedQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.ROOM_FINISHED));
    }

    @Bean
    public Queue interviewRecordingCompletedQueue(InterviewQueues queues) {
        return withDlq(queues.getRecordingCompleted());
    }

    @Bean
    public Queue interviewRecordingCompletedDlq(InterviewQueues queues) {
        return deadLetter(queues.getRecordingCompleted());
    }

    @Bean
    public Binding interviewRecordingCompletedBinding(Queue interviewRecordingCompletedQueue,
                                                      TopicExchange recruitEventsExchange) {
        return BindingBuilder.bind(interviewRecordingCompletedQueue).to(recruitEventsExchange)
                .with(EventRouting.routingKey(EventTypes.RECORDING_COMPLETED));
    }

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
