package com.recruit.interview.dto;

import java.time.Instant;

/**
 * 投递时间线元素。
 *
 * @param stageNo    阶段编码
 * @param stageName  阶段名称（按投递 stage_flow 解析，找不到时回退编码）
 * @param roundNo    轮次
 * @param status     动作后的状态
 * @param result     结果 PASS/FAIL/PENDING
 * @param operatorId 操作人
 * @param comment    备注
 * @param operatedAt 操作时间
 */
public record ApplicationTimelineView(
        String stageNo,
        String stageName,
        Integer roundNo,
        String status,
        String result,
        String operatorId,
        String comment,
        Instant operatedAt) {
}
