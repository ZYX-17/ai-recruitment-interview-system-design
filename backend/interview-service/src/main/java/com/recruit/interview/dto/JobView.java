package com.recruit.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 岗位详情/列表视图。
 *
 * @param id               岗位 ID
 * @param jobCode          岗位编码
 * @param title            岗位名称
 * @param deptId           部门 ID
 * @param deptName         部门名称快照
 * @param jdText           岗位描述
 * @param requirements     硬性要求
 * @param headcount        招聘人数
 * @param salaryRange      薪资范围
 * @param status           岗位状态
 * @param ownerHrId        负责人
 * @param publishedAt      发布时间
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 * @param version          版本号
 * @param applicationCount 投递数
 */
public record JobView(
        String id,
        String jobCode,
        String title,
        String deptId,
        String deptName,
        String jdText,
        JsonNode requirements,
        Integer headcount,
        String salaryRange,
        String status,
        String ownerHrId,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer version,
        long applicationCount) {
}
