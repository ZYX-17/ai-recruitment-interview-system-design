package com.recruit.resume.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.recruit.resume.entity.LocalMessage;
import com.recruit.resume.mapper.LocalMessageMapper;
import com.recruit.resume.support.OutboxRetry;
import com.recruit.resume.support.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Outbox 状态流转：SENT / FAILED + 退避 / DEAD。
 *
 * <p>发布成功以 RabbitMQ publisher confirm 的 ack 为准；失败按
 * {@link OutboxRetry} 退避，重试耗尽转 DEAD，等待人工补偿。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxStateService {

    private final LocalMessageMapper localMessageMapper;

    /**
     * 确认投递成功；已经是 SENT 的消息不重复更新。
     */
    public void markSent(String messageId) {
        localMessageMapper.update(null, Wrappers.<LocalMessage>lambdaUpdate()
                .eq(LocalMessage::getMessageId, messageId)
                .ne(LocalMessage::getStatus, OutboxStatus.SENT)
                .set(LocalMessage::getStatus, OutboxStatus.SENT)
                .set(LocalMessage::getNextRetryAt, null));
    }

    /**
     * 记录一次投递失败；成功后由 {@link #markSent(String)} 覆盖为 SENT。
     */
    public void markFailed(String messageId, String reason) {
        LocalMessage message = localMessageMapper.selectOne(Wrappers.<LocalMessage>lambdaQuery()
                .eq(LocalMessage::getMessageId, messageId)
                .last("LIMIT 1"));
        if (message == null
                || OutboxStatus.SENT.equals(message.getStatus())
                || OutboxStatus.DEAD.equals(message.getStatus())) {
            return;
        }

        int retries = (message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1;
        LambdaUpdateWrapper<LocalMessage> update = Wrappers.<LocalMessage>lambdaUpdate()
                .eq(LocalMessage::getMessageId, messageId)
                .ne(LocalMessage::getStatus, OutboxStatus.SENT)
                .set(LocalMessage::getRetryCount, retries);

        if (OutboxRetry.isDead(retries)) {
            update.set(LocalMessage::getStatus, OutboxStatus.DEAD)
                    .set(LocalMessage::getNextRetryAt, null);
            log.error("Outbox 重试耗尽转 DEAD messageId={}, reason={}", messageId, reason);
        } else {
            update.set(LocalMessage::getStatus, OutboxStatus.FAILED)
                    .set(LocalMessage::getNextRetryAt,
                            LocalDateTime.now(ZoneOffset.UTC).plusMinutes(OutboxRetry.backoffMinutes(retries)));
            log.warn("Outbox 投递失败 messageId={}, retry={}, reason={}", messageId, retries, reason);
        }
        localMessageMapper.update(null, update);
    }
}
