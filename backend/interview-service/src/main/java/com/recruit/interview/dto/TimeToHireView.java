package com.recruit.interview.dto;

import java.math.BigDecimal;

/**
 * 招聘周期（投递到入职）。
 *
 * @param sampleCount 样本数（时间范围内入职的去重投递）
 * @param avgDays     平均天数，无样本时为空
 * @param minDays     最短天数
 * @param maxDays     最长天数
 */
public record TimeToHireView(long sampleCount, BigDecimal avgDays, BigDecimal minDays, BigDecimal maxDays) {
}
