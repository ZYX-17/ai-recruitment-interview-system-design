package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code interview.result.submitted} 的负载（所有必填评价完成或 HR 确认结果）。
 *
 * <p>单份评价提交不发该事件，避免多面试官重复通知。</p>
 *
 * @param interviewId   面试 ID
 * @param applicationId 投递 ID
 * @param roundNo       轮次
 * @param result        结果，例如 PASS / REJECT / HOLD
 * @param totalScore    总分，可为空
 * @param submittedAt   提交时间（UTC）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewResultSubmittedPayload(
        String interviewId,
        String applicationId,
        Integer roundNo,
        String result,
        BigDecimal totalScore,
        Instant submittedAt) {
}
