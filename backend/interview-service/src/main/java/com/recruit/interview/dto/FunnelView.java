package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 招聘漏斗。
 *
 * @param stages          阶段列表（按流程顺序）
 * @param conversionRates 相邻阶段转化率，键如 {@code APPLIED_TO_SCREEN}
 */
public record FunnelView(List<FunnelStageView> stages, Map<String, BigDecimal> conversionRates) {
}
