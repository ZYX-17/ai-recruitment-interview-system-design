package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * {@code interview.scheduled} 的负载（room 建房、notify 发邀约）。
 *
 * <p>时间统一为 UTC {@link Instant}；roomId 在排期时可能还没有，建房回执后再回填。</p>
 *
 * @param interviewId    面试 ID
 * @param applicationId  投递 ID
 * @param roundNo        轮次
 * @param interviewerIds 面试官用户 ID 列表
 * @param candidateId    候选人 ID
 * @param candidateName  候选人姓名快照（通知模板使用）
 * @param startTime      开始时间（UTC）
 * @param endTime        结束时间（UTC）
 * @param mode           面试方式，例如 ONLINE
 * @param roomType       房间类型，例如 ONE_TO_ONE
 * @param roomId         房间 ID，排期阶段可为空
 * @param jobTitle       岗位名称快照（通知模板使用）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewScheduledPayload(
        String interviewId,
        String applicationId,
        Integer roundNo,
        List<String> interviewerIds,
        String candidateId,
        String candidateName,
        Instant startTime,
        Instant endTime,
        String mode,
        String roomType,
        String roomId,
        String jobTitle) {
}
