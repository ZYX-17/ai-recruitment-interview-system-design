package com.recruit.interview.dto;

import java.util.List;

/**
 * 面试官工作量列表。
 *
 * @param items 按场次倒序
 */
public record WorkloadView(List<WorkloadItemView> items) {
}
