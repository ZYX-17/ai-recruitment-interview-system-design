package com.recruit.interview.dto;

import java.time.Instant;

/**
 * 候选人进房结果。
 *
 * <p>只返回房间标识与前端路由；LiveKit 入会 token 仍由 room-service 的
 * {@code POST /api/room/rooms/{roomNo}/token} 签发，interview 不代签。</p>
 *
 * @param interviewId 面试 ID
 * @param roomId      房间 ID
 * @param joinUrl     房间页地址
 * @param status      面试状态
 * @param startTime   开始时间
 * @param endTime     结束时间
 */
public record PortalInterviewJoinView(
        String interviewId,
        String roomId,
        String joinUrl,
        String status,
        Instant startTime,
        Instant endTime) {
}
