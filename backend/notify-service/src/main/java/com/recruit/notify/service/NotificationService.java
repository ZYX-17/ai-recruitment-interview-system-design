package com.recruit.notify.service;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.contracts.event.EventEnvelope;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.notify.dto.MessageView;
import com.recruit.notify.entity.IdempotentConsumption;
import com.recruit.notify.entity.Notification;
import com.recruit.notify.entity.NotificationSendLog;
import com.recruit.notify.entity.NotificationTemplate;
import com.recruit.notify.mapper.IdempotentConsumptionMapper;
import com.recruit.notify.mapper.NotificationMapper;
import com.recruit.notify.mapper.NotificationSendLogMapper;
import com.recruit.notify.mapper.NotificationTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 通知消费与投递。
 *
 * <p>一条事件先写 {@code idempotent_consumption} 占位，再按渠道渲染模板、
 * 建通知行并投递；模板/变量错误直接抛 {@code NTF6001} 让消费方拒绝入 DLQ，
 * 不会落一条半成品通知。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 通知请求事件的消费组。 */
    public static final String CONSUMER_GROUP = "notify.notification-requested";

    private static final int MAX_RETRY = 3;

    private static final int[] RETRY_DELAY_MINUTES = {1, 5, 15};

    private static final Set<String> SUPPORTED_CHANNELS = Set.of("EMAIL", "INAPP", "SMS_MOCK");

    private static final DateTimeFormatter MSG_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final NotificationMapper notificationMapper;
    private final NotificationTemplateMapper templateMapper;
    private final NotificationSendLogMapper sendLogMapper;
    private final IdempotentConsumptionMapper idempotentMapper;
    private final TemplateRenderer templateRenderer;
    private final MailSenderService mailSenderService;

    /**
     * 消费 {@code notification.requested} 事件。
     *
     * @param envelope 事件信封
     */
    @Transactional
    public void handleNotificationRequested(EventEnvelope<NotificationRequestedPayload> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("通知事件不能为空");
        }
        handleEvent(CONSUMER_GROUP, envelope.getEventId(), envelope.getPayload());
    }

    /**
     * 处理一次通知请求（也供 resume 类事件映射后复用）。
     *
     * @param consumerGroup 消费组（幂等键前缀）
     * @param eventId       事件 ID
     * @param payload       通知请求
     */
    @Transactional
    public void handleEvent(String consumerGroup, String eventId, NotificationRequestedPayload payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        if (!tryConsume(consumerGroup, eventId)) {
            log.info("通知事件已消费，跳过: group={}, eventId={}", consumerGroup, eventId);
            return;
        }
        if (payload == null || payload.templateCode() == null || payload.templateCode().isBlank()) {
            throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "通知模板编码不能为空");
        }
        long receiverId = parseReceiverId(payload.receiverId());
        List<String> channels = normalizeChannels(payload.channel());
        for (String channel : channels) {
            NotificationTemplate template = templateMapper.selectActiveLatest(payload.templateCode(), channel);
            if (template == null) {
                throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID,
                        "通知模板不存在: " + payload.templateCode() + "/" + channel);
            }
            RenderedTemplate rendered = templateRenderer.render(template, payload.variables());
            if (alreadyDelivered(payload, channel, receiverId)) {
                log.info("通知已存在，跳过重复投递: bizType={}, bizId={}, channel={}",
                        payload.bizType(), payload.bizId(), channel);
                continue;
            }
            Notification notification = buildNotification(payload, channel, receiverId, rendered);
            notificationMapper.insert(notification);
            deliver(notification);
        }
    }

    /**
     * 管理员重发失败通知。
     *
     * <p>幂等语义：已发送/已读的记录直接返回当前状态，不重复发送。</p>
     *
     * @param id 通知 ID
     * @return 当前通知状态
     */
    @Transactional
    public MessageView resend(long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        if (!"FAILED".equals(notification.getStatus())) {
            return MessageView.of(notification);
        }
        int currentVersion = notification.getVersion() == null ? 0 : notification.getVersion();
        if (notificationMapper.resetForResend(id, currentVersion) == 0) {
            return MessageView.of(notificationMapper.selectById(id));
        }
        notification.setStatus("QUEUED");
        notification.setErrorMsg(null);
        notification.setRetryCount(0);
        notification.setNextRetryAt(null);
        notification.setVersion(currentVersion + 1);
        deliver(notification);
        log.info("audit action=NOTIFY_MESSAGE_RESEND operatorId={} notificationId={}",
                UserContext.getUserId(), id);
        Notification latest = notificationMapper.selectById(id);
        if (latest != null && "FAILED".equals(latest.getStatus())) {
            throw new BizException(ErrorCode.NOTIFY_SEND_FAILED, "通知重发失败，请稍后重试");
        }
        return MessageView.of(latest);
    }

    /**
     * 失败通知定时重试（1/5/15 分钟，最多 3 次）。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void retryFailed() {
        List<Notification> candidates = notificationMapper.selectRetryCandidates(50);
        for (Notification candidate : candidates) {
            try {
                sendEmail(candidate);
            } catch (RuntimeException e) {
                log.warn("通知重试异常: id={}, error={}", candidate.getId(), e.getMessage());
            }
        }
    }

    private void deliver(Notification notification) {
        String channel = notification.getChannel();
        if ("INAPP".equals(channel)) {
            notificationMapper.markSent(notification.getId());
            writeLog(notification.getId(), DeliveryOutcome.ok("INAPP", null, System.nanoTime()));
            return;
        }
        if ("SMS_MOCK".equals(channel)) {
            log.info("[SMS_MOCK] receiverId={}, title={}", notification.getReceiverId(), notification.getTitle());
            notificationMapper.markSent(notification.getId());
            writeLog(notification.getId(), DeliveryOutcome.ok("SMS_MOCK", null, System.nanoTime()));
            return;
        }
        sendEmail(notification);
    }

    private void sendEmail(Notification notification) {
        long start = System.nanoTime();
        if (notification.getReceiverEmail() == null || notification.getReceiverEmail().isBlank()) {
            DeliveryOutcome outcome = DeliveryOutcome.fail("SMTP", "收件人为空", start);
            writeLog(notification.getId(), outcome);
            notificationMapper.markFailed(notification.getId(), outcome.error(), MAX_RETRY, null);
            return;
        }
        DeliveryOutcome outcome = mailSenderService.send(
                notification.getReceiverEmail(), notification.getTitle(), notification.getContent());
        writeLog(notification.getId(), outcome);
        if (outcome.success()) {
            notificationMapper.markSent(notification.getId());
            return;
        }
        int retryCount = (notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1;
        LocalDateTime nextRetryAt = retryCount < MAX_RETRY
                ? LocalDateTime.now().plusMinutes(RETRY_DELAY_MINUTES[retryCount - 1])
                : null;
        notificationMapper.markFailed(notification.getId(), truncate(outcome.error(), 512), retryCount, nextRetryAt);
    }

    private boolean tryConsume(String consumerGroup, String eventId) {
        IdempotentConsumption consumption = new IdempotentConsumption();
        consumption.setConsumerGroup(consumerGroup);
        consumption.setEventId(eventId);
        consumption.setConsumedAt(LocalDateTime.now());
        try {
            idempotentMapper.insert(consumption);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private boolean alreadyDelivered(NotificationRequestedPayload payload, String channel, long receiverId) {
        String dedupKey = payload.dedupKey();
        if (dedupKey != null && !dedupKey.isBlank()) {
            return notificationMapper.selectByMsgNo(msgNoOf(dedupKey, channel)) != null;
        }
        return notificationMapper.countRecentByBiz(payload.bizType(), payload.bizId(), receiverId, channel) > 0;
    }

    private Notification buildNotification(NotificationRequestedPayload payload, String channel,
                                           long receiverId, RenderedTemplate rendered) {
        Notification notification = new Notification();
        notification.setMsgNo(payload.dedupKey() == null || payload.dedupKey().isBlank()
                ? randomMsgNo() : msgNoOf(payload.dedupKey(), channel));
        notification.setReceiverId(receiverId);
        notification.setReceiverEmail(payload.receiverEmail());
        notification.setChannel(channel);
        notification.setBizType(payload.bizType());
        notification.setBizId(payload.bizId());
        notification.setTitle(truncate(rendered.title(), 255));
        notification.setContent(rendered.content());
        notification.setStatus("QUEUED");
        notification.setRetryCount(0);
        notification.setVersion(0);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());
        return notification;
    }

    private void writeLog(long notificationId, DeliveryOutcome outcome) {
        NotificationSendLog logRow = new NotificationSendLog();
        logRow.setNotificationId(notificationId);
        logRow.setProvider(outcome.provider());
        logRow.setRequestId(outcome.requestId());
        logRow.setResult(outcome.success() ? "OK" : "FAIL");
        logRow.setErrorMsg(truncate(outcome.error(), 512));
        logRow.setCostMs(outcome.costMs());
        logRow.setCreatedAt(LocalDateTime.now());
        sendLogMapper.insert(logRow);
    }

    private static List<String> normalizeChannels(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return List.of("INAPP");
        }
        List<String> result = new ArrayList<>();
        for (String channel : channels) {
            if (channel == null || channel.isBlank()) {
                continue;
            }
            String normalized = channel.trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_CHANNELS.contains(normalized)) {
                throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "不支持的通知渠道: " + channel);
            }
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result.isEmpty() ? List.of("INAPP") : result;
    }

    private static long parseReceiverId(String receiverId) {
        if (receiverId == null || receiverId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收人不能为空");
        }
        try {
            return Long.parseLong(receiverId.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "接收人 ID 必须是数字");
        }
    }

    private static String msgNoOf(String dedupKey, String channel) {
        return "MSG-" + sha256Hex(dedupKey + "|" + channel).substring(0, 32).toUpperCase(Locale.ROOT);
    }

    private static String randomMsgNo() {
        return "MSG-" + LocalDateTime.now().format(MSG_TIME) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
