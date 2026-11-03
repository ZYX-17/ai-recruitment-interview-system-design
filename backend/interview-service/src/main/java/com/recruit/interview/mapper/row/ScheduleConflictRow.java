package com.recruit.interview.mapper.row;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 冲突查询行：已占用格子的参与者与所属面试。
 */
@Data
public class ScheduleConflictRow {

    private String participantType;

    private Long participantId;

    private LocalDate slotDate;

    private Integer slotNo;

    private Long interviewId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
