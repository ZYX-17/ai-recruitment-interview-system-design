package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等消费记录（recruit_resume.idempotent_consumption）。
 *
 * <p>唯一键 (consumer_group, event_id)：同一消费者组重复收到同一事件时直接跳过。</p>
 */
@Data
@TableName("idempotent_consumption")
public class IdempotentConsumption {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String consumerGroup;

    private String eventId;

    private LocalDateTime consumedAt;
}
