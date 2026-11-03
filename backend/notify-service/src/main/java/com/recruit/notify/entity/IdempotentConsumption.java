package com.recruit.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等消费表：与业务同库同事务，先占位后执行。
 */
@Data
@TableName("idempotent_consumption")
public class IdempotentConsumption {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String consumerGroup;

    private String eventId;

    private LocalDateTime consumedAt;
}
