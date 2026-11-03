package com.recruit.notify.service;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.notify.dto.MessagePageQuery;
import com.recruit.notify.dto.MessagePageView;
import com.recruit.notify.dto.MessageView;
import com.recruit.notify.entity.Notification;
import com.recruit.notify.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 站内信查询与已读：只允许操作当前登录用户自己的通知。
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final NotificationMapper notificationMapper;

    /**
     * 当前用户的通知分页。
     *
     * @param query 查询条件
     * @return 分页 + 未读数
     */
    public MessagePageView page(MessagePageQuery query) {
        long userId = currentUserId();
        long total = notificationMapper.countMessagePage(userId, query.getRead(),
                trimToNull(query.getStatus()), trimToNull(query.getChannel()));
        List<Notification> rows = total == 0 ? List.of() : notificationMapper.selectMessagePage(
                userId, query.getRead(), trimToNull(query.getStatus()), trimToNull(query.getChannel()),
                query.getPageSize(), (long) (query.getPageNum() - 1) * query.getPageSize());
        List<MessageView> views = new ArrayList<>(rows.size());
        for (Notification row : rows) {
            views.add(MessageView.of(row));
        }
        long pages = total == 0 ? 0 : total / query.getPageSize()
                + (total % query.getPageSize() == 0 ? 0 : 1);
        return new MessagePageView(views, total, query.getPageNum(), query.getPageSize(), pages,
                notificationMapper.countUnread(userId));
    }

    /**
     * 标记本人通知为已读（幂等）。
     *
     * @param id 通知 ID
     */
    public void markRead(long id) {
        long userId = currentUserId();
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        if (notification.getReceiverId() == null || notification.getReceiverId() != userId) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "只能标记自己的通知");
        }
        if ("READ".equals(notification.getStatus())) {
            return;
        }
        if (notificationMapper.markRead(id, userId) == 0) {
            Notification latest = notificationMapper.selectById(id);
            if (latest != null && "READ".equals(latest.getStatus())) {
                return;
            }
            throw new BizException(ErrorCode.PARAM_INVALID, "仅已送达的通知可标记已读");
        }
    }

    private static long currentUserId() {
        try {
            return Long.parseLong(UserContext.requireUserId());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED, "当前用户 ID 无效");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
