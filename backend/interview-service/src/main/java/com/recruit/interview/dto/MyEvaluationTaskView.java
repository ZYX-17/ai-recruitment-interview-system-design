package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 我的评价任务。
 *
 * @param interviewId  面试 ID
 * @param applicationId 投递 ID
 * @param jobTitle     岗位名称
 * @param candidateName 候选人姓名
 * @param roundNo      轮次
 * @param startTime    开始时间
 * @param endTime      结束时间
 * @param status       PENDING / SUBMITTED / REOPENED
 * @param evaluationId 评价 ID；待提交为空
 * @param result       已提交结果
 * @param totalScore   已提交总分
 * @param submittedAt  提交时间
 */
public record MyEvaluationTaskView(
        String interviewId,
        String applicationId,
        String jobTitle,
        String candidateName,
        Integer roundNo,
        Instant startTime,
        Instant endTime,
        String status,
        String evaluationId,
        String result,
        BigDecimal totalScore,
        Instant submittedAt) {
}
