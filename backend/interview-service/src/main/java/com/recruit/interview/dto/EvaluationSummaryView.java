package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单份评价摘要。
 *
 * @param evaluationId  评价 ID
 * @param interviewerId 面试官
 * @param interviewerName 面试官展示名（本地占位）
 * @param totalScore    总分
 * @param result        结果
 * @param comment       评语
 * @param submittedAt   提交时间
 */
public record EvaluationSummaryView(
        String evaluationId,
        String interviewerId,
        String interviewerName,
        BigDecimal totalScore,
        String result,
        String comment,
        Instant submittedAt) {
}
