package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 多面试官评价对比。
 *
 * @param interviewId     面试 ID
 * @param roundNo         轮次
 * @param templateVersion 模板版本
 * @param dimensions      维度均分/明细
 * @param evaluations     各评价摘要
 * @param divergence      分歧度（最高总分 - 最低总分）
 * @param suggestion      建议结果（2/3 PASS）
 */
public record EvaluationComparisonView(
        String interviewId,
        Integer roundNo,
        Integer templateVersion,
        List<DimensionComparisonView> dimensions,
        List<EvaluationSummaryView> evaluations,
        BigDecimal divergence,
        String suggestion) {
}
