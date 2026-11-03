package com.recruit.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知模板。{@code (template_code, channel, version)} 唯一，
 * 修改模板时新增 version+1 行，旧行置 ARCHIVED 保留。
 */
@Data
@TableName("notification_template")
public class NotificationTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String templateCode;

    private String channel;

    private Integer version;

    private String titleTpl;

    private String contentTpl;

    private String variablesJson;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
