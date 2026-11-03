package com.recruit.notify.dto;

import java.util.List;

/**
 * 站内信分页响应（在 PageResult 字段基础上补 {@code unreadCount}）。
 */
public record MessagePageView(
        List<MessageView> list,
        long total,
        long pageNum,
        long pageSize,
        long pages,
        long unreadCount) {
}
