package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表 / Outbox（recruit_resume.local_message）。
 *
 * <p>业务表与 Outbox 同事务写入，后台发布器扫描 PENDING 投递 RabbitMQ；
 * 失败按 1/2/4/8/16 分钟退避，5 次转 DEAD。</p>
 */
@Data
@TableName("local_message")
public class LocalMessage {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 事件 ID（EventEnvelope.eventId），全局唯一。 */
    private String messageId;

    private String eventType;

    private Integer eventVersion;

    private String aggregateId;

    private String traceId;

    private String exchange;

    private String routingKey;

    /** EventEnvelope 的 JSON 序列化结果。 */
    private String payload;

    /** PENDING/SENT/FAILED/DEAD。 */
    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
