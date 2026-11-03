package com.recruit.interview.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 面试纪要视图。
 *
 * @param id                   纪要 ID
 * @param interviewId          面试 ID
 * @param aiTaskId             AI 任务 ID
 * @param transcriptObjectKey  转写对象键
 * @param segmentCount         转写片段数
 * @param minutesMd            纪要 Markdown 草稿
 * @param aiEvaluation         结构化 AI 结果
 * @param reviewStatus         PENDING/CONFIRMED/REJECTED/INSUFFICIENT_TRANSCRIPT
 * @param reviewComment        确认/驳回意见
 * @param reviewerId           复核人
 * @param reviewedAt           复核时间
 * @param model                模型
 * @param promptVersion        Prompt 版本
 * @param version              乐观锁版本
 */
public record MinutesView(
        String id,
        String interviewId,
        String aiTaskId,
        String transcriptObjectKey,
        Integer segmentCount,
        String minutesMd,
        Map<String, Object> aiEvaluation,
        String reviewStatus,
        String reviewComment,
        String reviewerId,
        Instant reviewedAt,
        String model,
        Integer promptVersion,
        Integer version) {
}
