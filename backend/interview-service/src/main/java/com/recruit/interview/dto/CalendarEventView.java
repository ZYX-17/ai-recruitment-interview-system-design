package com.recruit.interview.dto;

import java.time.Instant;
import java.util.List;

/**
 * 日历事件。
 *
 * @param id             面试 ID
 * @param applicationId  投递 ID
 * @param jobId          岗位 ID
 * @param jobTitle       岗位名称
 * @param candidateId    候选人 ID
 * @param candidateName  候选人姓名
 * @param roundNo        轮次
 * @param interviewerIds 面试官
 * @param startTime      开始时间（UTC，前端按本地时区展示）
 * @param endTime        结束时间（UTC）
 * @param status         状态
 * @param mode           面试方式
 * @param roomType       房间类型
 */
public record CalendarEventView(
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
        String status,
        String mode,
        String roomType) {
}
