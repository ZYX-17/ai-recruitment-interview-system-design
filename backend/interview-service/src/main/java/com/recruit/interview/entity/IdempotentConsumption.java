package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 幂等消费记录（{@code idempotent_consumption}）。
 *
 * <p>{@code (consumer_group, event_id)} 唯一；插入冲突即代表已消费。</p>
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
