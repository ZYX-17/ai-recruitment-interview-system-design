package com.recruit.interview.dto;

/**
 * 面试官工作量。
 *
 * @param interviewerId 面试官用户 ID
 * @param total         时间范围内面试场次（排除 CANCELLED）
 * @param completed     已完成场次
 * @param noShow        未到场场次（单列，不计入完成）
 */
public record WorkloadItemView(String interviewerId, long total, long completed, long noShow) {
}
