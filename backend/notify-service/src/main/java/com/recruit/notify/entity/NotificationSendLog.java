package com.recruit.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知发送日志（不记敏感内容）。
 */
@Data
@TableName("notification_send_log")
public class NotificationSendLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long notificationId;

    private String provider;

    private String requestId;

    private String result;

    private String errorMsg;

    private Integer costMs;

    private LocalDateTime createdAt;
}
