package com.recruit.resume.dto;

/**
 * 创建简历的结果。
 *
 * @param resumeId    简历 ID（字符串）
 * @param resumeNo    简历编号
 * @param personId    候选人 ID
 * @param taskNo      解析任务号；duplicated=true 时为已有任务号（可能为 null）
 * @param parseStatus 当前解析状态
 * @param duplicated  true 表示 SHA-256 命中已有简历，未新建
 */
public record CreateResumeResponse(
        String resumeId,
        String resumeNo,
        String personId,
        String taskNo,
        String parseStatus,
        boolean duplicated
) {
}
