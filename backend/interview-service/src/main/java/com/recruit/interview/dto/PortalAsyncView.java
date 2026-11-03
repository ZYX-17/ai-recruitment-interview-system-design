package com.recruit.interview.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 候选人门户异步面试视图：只暴露作答所需字段。
 *
 * @param paperNo          试卷编号
 * @param positionTitle    岗位名称
 * @param candidateName    候选人姓名
 * @param status           RELEASED/IN_PROGRESS/SUBMITTED/SCORED
 * @param questionCount    题目数
 * @param durationLimitSec 总时长上限（秒）
 * @param settings         拍摄/防作弊设置
 * @param expireAt         邀请过期时间
 * @param startedAt        开始时间
 * @param submittedAt      提交时间
 * @param questions        题目（已提交后不返回）
 */
public record PortalAsyncView(
        String paperNo,
        String positionTitle,
        String candidateName,
        String status,
        Integer questionCount,
        Integer durationLimitSec,
        Map<String, Object> settings,
        Instant expireAt,
        Instant startedAt,
        Instant submittedAt,
        List<AsyncQuestionView> questions) {
}
