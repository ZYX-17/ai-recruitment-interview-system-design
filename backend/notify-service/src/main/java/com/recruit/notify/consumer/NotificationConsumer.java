package com.recruit.notify.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.recruit.contracts.event.EventEnvelope;
import com.recruit.contracts.event.payload.CandidateDuplicatedPayload;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.contracts.event.payload.ResumeParseFailedPayload;
import com.recruit.contracts.event.payload.TalentFollowupDuePayload;
import com.recruit.notify.service.NotificationEventAdapter;
import com.recruit.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通知消费者：统一通知请求 + resume 域三类提醒。
 *
 * <p>手动 ack：处理成功 ack；模板/变量等永久错误 basicNack 不重入队，
 * 堵塞到 DLQ 便于修模板后重放。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final TypeReference<EventEnvelope<Map<String, Object>>> ENVELOPE_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final NotificationEventAdapter eventAdapter;

    /**
     * 消费 {@code notification.requested}。
     */
    @RabbitListener(queues = "#{@notifyQueues.notificationRequested}")
    public void onNotificationRequested(Message message, Channel channel) {
        consume(message, channel, "notification.requested", envelope -> {
            NotificationRequestedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), NotificationRequestedPayload.class);
            notificationService.handleEvent(NotificationService.CONSUMER_GROUP,
                    envelope.getEventId(), payload);
        });
    }

    /**
     * 消费 {@code resume.parse.failed}。
     */
    @RabbitListener(queues = "#{@notifyQueues.resumeParseFailed}")
    public void onResumeParseFailed(Message message, Channel channel) {
        consume(message, channel, "resume.parse.failed", envelope -> {
            ResumeParseFailedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), ResumeParseFailedPayload.class);
            NotificationRequestedPayload request = eventAdapter.fromResumeParseFailed(payload);
            if (request == null) {
                log.warn("简历解析失败事件缺接收人，跳过通知: eventId={}, resumeId={}",
                        envelope.getEventId(), payload == null ? null : payload.resumeId());
                return;
            }
            notificationService.handleEvent("notify.resume-parse-failed", envelope.getEventId(), request);
        });
    }

    /**
     * 消费 {@code candidate.duplicated}。
     */
    @RabbitListener(queues = "#{@notifyQueues.candidateDuplicated}")
    public void onCandidateDuplicated(Message message, Channel channel) {
        consume(message, channel, "candidate.duplicated", envelope -> {
            CandidateDuplicatedPayload payload = objectMapper.convertValue(
                    envelope.getPayload(), CandidateDuplicatedPayload.class);
            NotificationRequestedPayload request = eventAdapter.fromCandidateDuplicated(payload);
            if (request == null) {
                log.warn("候选人查重事件缺接收人，跳过通知: eventId={}, personId={}",
                        envelope.getEventId(), payload == null ? null : payload.personId());
                return;
            }
            notificationService.handleEvent("notify.candidate-duplicated", envelope.getEventId(), request);
        });
    }

    /**
     * 消费 {@code talent.followup.due}。
     */
    @RabbitListener(queues = "#{@notifyQueues.talentFollowupDue}")
    public void onTalentFollowupDue(Message message, Channel channel) {
        consume(message, channel, "talent.followup.due", envelope -> {
            TalentFollowupDuePayload payload = objectMapper.convertValue(
                    envelope.getPayload(), TalentFollowupDuePayload.class);
            NotificationRequestedPayload request = eventAdapter.fromTalentFollowupDue(payload);
            if (request == null) {
                log.warn("回访提醒事件缺接收人，跳过通知: eventId={}, talentId={}",
                        envelope.getEventId(), payload == null ? null : payload.talentId());
                return;
            }
            notificationService.handleEvent("notify.talent-followup-due", envelope.getEventId(), request);
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
            log.error("通知事件消费失败，转入 DLQ: event={}, error={}",
                    eventLabel, e.getMessage(), e);
            try {
                channel.basicNack(tag, false, false);
            } catch (IOException ioException) {
                log.error("拒绝消息失败: event={}", eventLabel, ioException);
            }
        }
    }
}
