package com.recruit.interview.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 门户岗位视图（I-10）。
 *
 * <p>只暴露候选人需要的信息：不含负责人、部门 ID、版本号与投递统计，
 * 避免把内部管理字段带到公开页。</p>
 *
 * @param id          岗位 ID
 * @param jobCode     岗位编码
 * @param title       岗位名称
 * @param deptName    部门名称快照
 * @param jdText      岗位描述
 * @param requirements 硬性要求
 * @param headcount   招聘人数
 * @param salaryRange 薪资范围
 * @param publishedAt 发布时间
 */
public record PortalJobView(
        String id,
        String jobCode,
        String title,
        String deptName,
        String jdText,
        JsonNode requirements,
        Integer headcount,
        String salaryRange,
        Instant publishedAt) {
}
