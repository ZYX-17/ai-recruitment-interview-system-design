package com.recruit.interview.service;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.OutboxMessageView;
import com.recruit.interview.dto.OutboxPageQuery;
import com.recruit.interview.entity.LocalMessage;
import com.recruit.interview.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Outbox 管理（P11-3，X-03/X-12）。
 *
 * <p>只读列表 + DEAD/FAILED 重放；重放置 PENDING 后由扫描器 5s 内重新投递。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxAdminService {

    private static final Set<String> STATUSES = Set.of(
            "PENDING", "SENT", "CONFIRMED", "FAILED", "DEAD");

    private final LocalMessageMapper messageMapper;

    /**
     * 管理页列表。
     *
     * @param query 过滤与分页
     * @return 分页消息
     */
    public PageResult<OutboxMessageView> page(OutboxPageQuery query) {
        String status = normalizeStatus(query.getStatus());
        String eventType = trimToNull(query.getEventType());
        long total = messageMapper.countOutboxPage(status, eventType);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        List<OutboxMessageView> views = new ArrayList<>(query.getPageSize());
        for (LocalMessage message : messageMapper.selectOutboxPage(status, eventType,
                query.getPageSize(), offset)) {
            views.add(toView(message));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 重放 DEAD/FAILED 消息。
     *
     * @param id 消息 ID
     * @return 重放后的消息
     */
    public OutboxMessageView replay(long id) {
        LocalMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "Outbox 消息不存在");
        }
        if (!"DEAD".equals(message.getStatus()) && !"FAILED".equals(message.getStatus())) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "仅 DEAD/FAILED 消息可重放");
        }
        messageMapper.replay(id, LocalDateTime.now(ZoneOffset.UTC));
        log.info("audit action=OUTBOX_REPLAY operatorId={} messageId={} eventType={}",
                UserContext.getUserId(), message.getMessageId(), message.getEventType());
        return toView(messageMapper.selectById(id));
    }

    private static String normalizeStatus(String value) {
        String status = trimToNull(value);
        if (status == null) {
            return null;
        }
        status = status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Outbox 状态非法: " + value);
        }
        return status;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static OutboxMessageView toView(LocalMessage message) {
        return new OutboxMessageView(
                String.valueOf(message.getId()),
                message.getMessageId(),
                message.getEventType(),
                message.getEventVersion(),
                message.getAggregateId(),
                message.getStatus(),
                message.getRetryCount(),
                message.getNextRetryAt() == null ? null
                        : message.getNextRetryAt().toInstant(ZoneOffset.UTC),
                message.getCreatedAt() == null ? null
                        : message.getCreatedAt().toInstant(ZoneOffset.UTC),
                message.getUpdatedAt() == null ? null
                        : message.getUpdatedAt().toInstant(ZoneOffset.UTC),
                message.getVersion());
    }
}
