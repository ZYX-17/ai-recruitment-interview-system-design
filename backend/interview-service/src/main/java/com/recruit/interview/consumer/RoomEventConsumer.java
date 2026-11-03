package com.recruit.interview.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.contracts.event.EventEnvelope;
import com.recruit.contracts.event.payload.RecordingCompletedPayload;
import com.recruit.contracts.event.payload.RoomCreateFailedPayload;
import com.recruit.contracts.event.payload.RoomCreatedPayload;
import com.recruit.contracts.event.payload.RoomFinishedPayload;
import com.recruit.interview.service.RoomEventService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * room 事件消费者（P10-2/P10-3/P10-5）。
 *
 * <p>手动 ack：成功 ack；异常 basicNack 不重入队，转 DLQ 便于排查。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomEventConsumer {

    private static final TypeReference<EventEnvelope<Map<String, Object>>> ENVELOPE_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final RoomEventService roomEventService;

    /**
     * 消费 {@code room.created}。
     */
    @RabbitListener(queues = "#{@interviewQueues.roomCreated}")
    public void onRoomCreated(Message message, Channel channel) {
        consume(message, channel, "room.created", envelope -> {
            RoomCreatedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), RoomCreatedPayload.class);
            roomEventService.handleRoomCreated(envelope.getEventId(), payload);
        });
    }

    /**
     * 消费 {@code room.create.failed}。
     */
    @RabbitListener(queues = "#{@interviewQueues.roomCreateFailed}")
    public void onRoomCreateFailed(Message message, Channel channel) {
        consume(message, channel, "room.create.failed", envelope -> {
            RoomCreateFailedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), RoomCreateFailedPayload.class);
            roomEventService.handleRoomFailed(envelope.getEventId(), payload);
        });
    }

    /**
     * 消费 {@code room.finished}。
     */
    @RabbitListener(queues = "#{@interviewQueues.roomFinished}")
    public void onRoomFinished(Message message, Channel channel) {
        consume(message, channel, "room.finished", envelope -> {
            RoomFinishedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), RoomFinishedPayload.class);
            roomEventService.handleRoomFinished(envelope.getEventId(), payload);
        });
    }

    /**
     * 消费 {@code recording.completed}。
     */
    @RabbitListener(queues = "#{@interviewQueues.recordingCompleted}")
    public void onRecordingCompleted(Message message, Channel channel) {
        consume(message, channel, "recording.completed", envelope -> {
            RecordingCompletedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), RecordingCompletedPayload.class);
            roomEventService.handleRecordingCompleted(envelope.getEventId(), payload);
        });
    }

    private void consume(Message message, Channel channel, String eventLabel,
                         Consumer<EventEnvelope<Map<String, Object>>> handler) {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            EventEnvelope<Map<String, Object>> envelope =
                    objectMapper.readValue(message.getBody(), ENVELOPE_TYPE);
            handler.accept(envelope);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("面试事件消费失败，转入 DLQ: event={}, error={}",
                    eventLabel, e.getMessage(), e);
            try {
                channel.basicNack(tag, false, false);
            } catch (IOException ioException) {
                log.error("拒绝消息失败: event={}", eventLabel, ioException);
            }
        }
    }
}
