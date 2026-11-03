package com.recruit.interview.dto;

import java.math.BigDecimal;

/**
 * AI 草稿的单维度建议。
 *
 * @param dimCode        维度编码
 * @param dimName        维度名称
 * @param suggestedScore 建议分
 * @param reason         依据说明
 */
public record AiDimensionSuggestionView(
        String dimCode,
        String dimName,
        BigDecimal suggestedScore,
        String reason) {
}
