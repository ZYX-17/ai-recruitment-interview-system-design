package com.recruit.notify.dto;

import com.recruit.notify.entity.Notification;

import java.time.LocalDateTime;

/**
 * 站内信 / 通知展示。
 */
public record MessageView(
        String id,
        String msgNo,
        String channel,
        String bizType,
        String bizId,
        String title,
        String content,
        String status,
        String errorMsg,
        LocalDateTime readAt,
        LocalDateTime createdAt) {

    /**
     * 实体转展示对象。
     *
     * @param n 通知实体
     * @return 展示对象
     */
    public static MessageView of(Notification n) {
        return new MessageView(
                n.getId() == null ? null : String.valueOf(n.getId()),
                n.getMsgNo(),
                n.getChannel(),
                n.getBizType(),
                n.getBizId(),
                n.getTitle(),
                n.getContent(),
                n.getStatus(),
                n.getErrorMsg(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}
