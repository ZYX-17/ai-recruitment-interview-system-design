package com.recruit.interview.dto;

import java.time.Instant;
import java.util.List;

/**
 * 内部进房预检视图（room-service 用）。
 *
 * @param interviewId    面试 ID
 * @param candidateId    候选人 ID
 * @param interviewerIds 面试官用户 ID 列表
 * @param roomId         房间 ID，未创建时为空
 * @param roomType       房间类型
 * @param mode           面试方式
 * @param status         面试状态
 * @param startTime      开始时间（UTC）
 * @param endTime        结束时间（UTC）
 */
public record InterviewParticipantsView(
        String interviewId,
        String candidateId,
        List<String> interviewerIds,
        String roomId,
        String roomType,
        String mode,
        String status,
        Instant startTime,
        Instant endTime) {
}
