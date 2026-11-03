package com.recruit.interview.event;

import com.recruit.interview.entity.LocalMessage;
import com.recruit.interview.mapper.LocalMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Outbox 投递扫描器（P11-3 前置能力，docs/4 §7.8）。
 *
 * <p>每 {@code scan-interval-ms} 扫描到期的 {@code PENDING} 消息：confirm 成功置 {@code SENT}，
 * 失败按 1/2/4/8/16 分钟退避，超过上限置 {@code DEAD}。多实例下允许重复投递，
 * 消费端以 {@code idempotent_consumption} 保证幂等。</p>
 */
@Slf4j
@Component
public class OutboxRelay {

    private final LocalMessageMapper messageMapper;
    private final OutboxSender sender;
    private final boolean enabled;
    private final int batchSize;
    private final int maxRetry;

    /**
     * @param messageMapper Outbox Mapper
     * @param sender        投递通道
     * @param enabled       是否启用扫描
     * @param batchSize     单批上限
     * @param maxRetry      最大重试次数
     */
    public OutboxRelay(LocalMessageMapper messageMapper, OutboxSender sender,
                       @Value("${recruit.outbox.enabled:true}") boolean enabled,
                       @Value("${recruit.outbox.batch-size:100}") int batchSize,
                       @Value("${recruit.outbox.max-retry:5}") int maxRetry) {
        this.messageMapper = messageMapper;
        this.sender = sender;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxRetry = maxRetry;
    }

    /**
     * 定时扫描并投递。
     */
    @Scheduled(fixedDelayString = "${recruit.outbox.scan-interval-ms:5000}",
            initialDelayString = "${recruit.outbox.initial-delay-ms:5000}")
    public void scan() {
        if (!enabled) {
            return;
        }
        try {
            relayBatch();
        } catch (RuntimeException e) {
            log.warn("Outbox 扫描异常: {}", e.getMessage());
        }
    }

    /**
     * 执行一批投递（可被测试或管理端手动触发）。
     *
     * @return 确认成功的条数
     */
    public int relayBatch() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<LocalMessage> pending = messageMapper.selectPendingForRelay(now, batchSize);
        int sent = 0;
        for (LocalMessage message : pending) {
            if (relayOne(message, now)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean relayOne(LocalMessage message, LocalDateTime now) {
        String error;
        try {
            error = sender.send(message.getExchange(), message.getRoutingKey(), message.getPayload());
        } catch (RuntimeException e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        if (error == null) {
            messageMapper.markSent(message.getId(), now);
            return true;
        }
        int retryCount = (message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1;
        if (retryCount > maxRetry) {
            messageMapper.markDead(message.getId(), retryCount, now);
            log.error("Outbox 投递超过重试上限，置 DEAD: id={}, eventType={}, error={}",
                    message.getId(), message.getEventType(), error);
            return false;
        }
        LocalDateTime nextRetryAt = now.plusMinutes(1L << (retryCount - 1));
        messageMapper.markRetry(message.getId(), retryCount, nextRetryAt, now);
        log.warn("Outbox 投递失败，{} 后重试({}/{}): id={}, error={}",
                nextRetryAt, retryCount, maxRetry, message.getId(), error);
        return false;
    }
}
