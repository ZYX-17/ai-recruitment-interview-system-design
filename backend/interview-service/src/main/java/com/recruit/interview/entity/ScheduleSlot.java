package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排期格子（{@code schedule_slot}）。
 *
 * <p>唯一键 {@code (participant_type, participant_id, slot_date, slot_no)} 是并发冲突的最终兜底：
 * 写入时捕获 {@code DuplicateKeyException} 回滚并返回 409 {@code ITV3101}。
 * 取消/改期物理删除旧行，避免“已取消仍占位”。</p>
 */
@Data
@TableName("schedule_slot")
public class ScheduleSlot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String participantType;

    private Long participantId;

    private LocalDate slotDate;

    private Integer slotNo;

    private Long interviewId;

    private LocalDateTime createdAt;
}
