package com.recruit.interview.dto;

import java.time.Instant;
import java.util.List;

/**
 * 面试安排视图。
 *
 * @param id             面试 ID
 * @param applicationId  投递 ID
 * @param jobId          岗位 ID
 * @param jobTitle       岗位名称快照
 * @param candidateId    候选人 ID
 * @param candidateName  候选人姓名快照
 * @param roundNo        轮次
 * @param interviewerIds 面试官用户 ID
 * @param startTime      开始时间（UTC）
 * @param endTime        结束时间（UTC）
 * @param mode           面试方式
 * @param roomType       房间类型
 * @param status         状态
 * @param roomId         房间 ID，可为空
 * @param cancelReason   取消原因
 * @param note           排期备注
 * @param version        版本号
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 */
public record ScheduleView(
        String id,
        String applicationId,
        String jobId,
        String jobTitle,
        String candidateId,
        String candidateName,
        Integer roundNo,
        List<String> interviewerIds,
        Instant startTime,
        Instant endTime,
        String mode,
        String roomType,
        String status,
        String roomId,
        String cancelReason,
        String note,
        Integer version,
        Instant createdAt,
        Instant updatedAt) {
}
