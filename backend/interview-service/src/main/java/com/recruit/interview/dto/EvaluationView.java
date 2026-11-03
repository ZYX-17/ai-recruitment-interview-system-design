package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 评价视图。
 *
 * @param id              评价 ID
 * @param interviewId     面试 ID
 * @param roundNo         轮次
 * @param interviewerId   面试官
 * @param dimensionScores 维度得分
 * @param comment         评语
 * @param result          结果
 * @param totalScore      加权总分（1 位小数）
 * @param templateVersion 模板版本
 * @param status          SUBMITTED / REOPENED
 * @param submittedAt     提交时间
 * @param reopenedBy      重开人
 * @param reopenedAt      重开时间
 * @param reopenReason    重开原因
 * @param version         版本号
 */
public record EvaluationView(
        String id,
        String interviewId,
        Integer roundNo,
        String interviewerId,
        Map<String, BigDecimal> dimensionScores,
        String comment,
        String result,
        BigDecimal totalScore,
        Integer templateVersion,
        String status,
        Instant submittedAt,
        String reopenedBy,
        Instant reopenedAt,
        String reopenReason,
        Integer version) {
}
