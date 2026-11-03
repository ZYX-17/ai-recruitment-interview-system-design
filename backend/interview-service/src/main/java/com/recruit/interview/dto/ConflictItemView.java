package com.recruit.interview.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 单条冲突明细。
 *
 * @param participantType     参与者类型
 * @param participantId       参与者 ID
 * @param participantName     参与者展示名（本地占位名，跨域姓名由前端补齐）
 * @param slotDate            冲突格子的业务日期
 * @param slotNo              冲突格子的 30 分钟序号
 * @param existingInterviewId 已占用该格的面试 ID
 * @param startTime           已有面试开始时间
 * @param endTime             已有面试结束时间
 */
public record ConflictItemView(
        String participantType,
        String participantId,
        String participantName,
        LocalDate slotDate,
        Integer slotNo,
        String existingInterviewId,
        Instant startTime,
        Instant endTime) {
}
