package com.recruit.interview.dto;

import java.time.Instant;

/**
 * 门户面试视图。
 *
 * @param id         面试 ID
 * @param jobId      岗位 ID
 * @param jobTitle   岗位名称快照
 * @param roundNo    轮次
 * @param startTime  开始时间
 * @param endTime    结束时间
 * @param mode       ONLINE/OFFLINE
 * @param roomType   ONE_TO_ONE/ONE_TO_MANY
 * @param roomId     房间 ID，未建房时为 null
 * @param status     面试状态
 * @param joinable   当前是否处于可进房窗口
 */
public record PortalInterviewView(
        String id,
        String jobId,
        String jobTitle,
        Integer roundNo,
        Instant startTime,
        Instant endTime,
        String mode,
        String roomType,
        String roomId,
        String status,
        boolean joinable) {
}
