package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 投递视图。
 *
 * @param id           投递 ID
 * @param jobId        岗位 ID
 * @param jobTitle     岗位名称快照
 * @param personId     候选人 ID
 * @param personName   候选人姓名快照
 * @param currentStage 当前阶段
 * @param roundNo      当前轮次
 * @param stageFlow    投递时复制的流程
 * @param matchScore   匹配分
 * @param status       投递状态
 * @param rejectReason 淘汰原因
 * @param source       来源
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param version      版本号
 */
public record ApplicationView(
        String id,
        String jobId,
        String jobTitle,
        String personId,
        String personName,
        String currentStage,
        Integer roundNo,
        List<StageDef> stageFlow,
        BigDecimal matchScore,
        String status,
        String rejectReason,
        String source,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {
}
