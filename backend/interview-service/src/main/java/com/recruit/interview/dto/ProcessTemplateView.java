package com.recruit.interview.dto;

import java.time.Instant;
import java.util.List;

/**
 * 流程模板视图。
 *
 * @param id          模板 ID
 * @param jobId       岗位 ID
 * @param templateVer 模板版本
 * @param stageFlow   阶段定义
 * @param scoreDims   评分维度
 * @param status      ACTIVE / INACTIVE
 * @param createdBy   创建人
 * @param createdAt   创建时间
 * @param version     版本号
 */
public record ProcessTemplateView(
        String id,
        String jobId,
        Integer templateVer,
        List<StageDef> stageFlow,
        List<ScoreDim> scoreDims,
        String status,
        String createdBy,
        Instant createdAt,
        Integer version) {
}
