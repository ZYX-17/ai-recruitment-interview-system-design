package com.recruit.interview.dto;

import java.time.Instant;
import java.util.List;

/**
 * AI 评价草稿（只读）。
 *
 * <p>必须由面试官人工确认后，前端再调用正式提交接口；AI 不直接写 {@code evaluation}。</p>
 *
 * @param dimensionSuggestions 维度建议
 * @param evidence             证据片段
 * @param risks                风险点
 * @param model                模型标识
 * @param transcriptSegmentCount 转写片段数
 * @param generatedAt          生成时间
 */
public record AiDraftView(
        List<AiDimensionSuggestionView> dimensionSuggestions,
        List<String> evidence,
        List<String> risks,
        String model,
        Integer transcriptSegmentCount,
        Instant generatedAt) {
}
