package com.recruit.interview.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 异步试卷视图（管理端）。
 *
 * @param id               试卷 ID
 * @param paperNo          试卷编号
 * @param applicationId    投递 ID
 * @param jobId            岗位 ID
 * @param jobTitle         岗位名称快照
 * @param personId         候选人 ID
 * @param personName       候选人姓名
 * @param status           DRAFT/RELEASED/IN_PROGRESS/SUBMITTED/SCORED
 * @param questionCount    题目数
 * @param durationLimitSec 总时长上限（秒）
 * @param settings         拍摄/防作弊设置
 * @param totalScore       加权总分
 * @param inviteExpireAt   邀请链接过期时间
 * @param startedAt        开始作答时间
 * @param submittedAt      提交时间
 * @param reviewedAt       最后复核时间
 * @param questions        题目列表
 * @param createdAt        创建时间
 * @param version          乐观锁版本
 */
public record AsyncPaperView(
        String id,
        String paperNo,
        String applicationId,
        String jobId,
        String jobTitle,
        String personId,
        String personName,
        String status,
        Integer questionCount,
        Integer durationLimitSec,
        Map<String, Object> settings,
        BigDecimal totalScore,
        Instant inviteExpireAt,
        Instant startedAt,
        Instant submittedAt,
        Instant reviewedAt,
        List<AsyncQuestionView> questions,
        Instant createdAt,
        Integer version) {
}
