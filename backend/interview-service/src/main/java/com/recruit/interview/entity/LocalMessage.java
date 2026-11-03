package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表（Outbox）。
 *
 * <p>业务写库与消息插入必须同一事务；扫描器按 {@code status/next_retry_at}
 * 投递，失败退避，超过 5 次置 DEAD。</p>
 */
@Data
@TableName("local_message")
public class LocalMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String messageId;

    private String eventType;

    private Integer eventVersion;

    private String aggregateId;

    private String traceId;

    private String exchange;

    private String routingKey;

    private String payload;

    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
