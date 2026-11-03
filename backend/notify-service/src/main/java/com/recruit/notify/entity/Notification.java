package com.recruit.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知表。一条记录对应一个渠道（EMAIL / INAPP）的一次投递。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String msgNo;

    private Long receiverId;

    private String receiverEmail;

    private String channel;

    private String bizType;

    private String bizId;

    private String title;

    private String content;

    private String status;

    private String errorMsg;

    private LocalDateTime sentAt;

    private LocalDateTime readAt;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
