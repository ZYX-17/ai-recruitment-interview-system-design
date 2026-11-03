package com.recruit.notify.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.recruit.notify.service.NotificationEventAdapter;
import com.recruit.notify.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 消费者 ack/nack 行为单测。
 */
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Channel channel;

    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationConsumer(new ObjectMapper().findAndRegisterModules(),
                notificationService, new NotificationEventAdapter());
    }

    @Test
    void validRequestIsDispatchedAndAcked() throws Exception {
        Message message = message("{\"eventId\":\"e1\",\"eventType\":\"notification.requested\","
                + "\"payload\":{\"templateCode\":\"interview_invite\",\"receiverId\":\"60001\","
                + "\"channel\":[\"INAPP\"],\"bizType\":\"INTERVIEW_INVITE\",\"bizId\":\"90001\","
                + "\"variables\":{\"candidateName\":\"张三\",\"jobTitle\":\"Java\"},\"dedupKey\":\"k\"}}");

        consumer.onNotificationRequested(message, channel);

        verify(notificationService).handleEvent(eq(NotificationService.CONSUMER_GROUP), eq("e1"), any());
        verify(channel).basicAck(1L, false);
    }

    @Test
    void malformedMessageIsRejectedToDlq() throws Exception {
        consumer.onNotificationRequested(message("{not-json"), channel);

        verify(notificationService, never()).handleEvent(any(), any(), any());
        verify(channel).basicNack(1L, false, false);
    }

    private static Message message(String body) {
        Message message = new Message(body.getBytes(StandardCharsets.UTF_8));
        message.getMessageProperties().setDeliveryTag(1L);
        return message;
    }
}
