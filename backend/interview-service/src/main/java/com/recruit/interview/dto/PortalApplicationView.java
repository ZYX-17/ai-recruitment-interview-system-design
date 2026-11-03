package com.recruit.interview.dto;

import java.time.Instant;

/**
 * 门户投递视图（I-09）。
 *
 * @param id           投递 ID
 * @param jobId        岗位 ID
 * @param jobTitle     岗位名称快照
 * @param status       投递状态
 * @param currentStage 当前阶段
 * @param roundNo      当前轮次
 * @param source       投递来源
 * @param rejectReason 淘汰原因
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param version      版本号
 */
public record PortalApplicationView(
        String id,
        String jobId,
        String jobTitle,
        String status,
        String currentStage,
        Integer roundNo,
        String source,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {
}
