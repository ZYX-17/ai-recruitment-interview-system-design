package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 维度对比（雷达图数据）。
 *
 * @param dimCode 维度编码
 * @param dimName 维度名称
 * @param weight  权重
 * @param average 平均分
 * @param scores  各面试官得分
 */
public record DimensionComparisonView(
        String dimCode,
        String dimName,
        BigDecimal weight,
        BigDecimal average,
        List<BigDecimal> scores) {
}
