package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试安排（{@code interview}）。
 *
 * <p>状态机见 ADR-004：{@code ROOM_PENDING → SCHEDULED → IN_PROGRESS → COMPLETED}，
 * 另有 {@code CANCELLED/NO_SHOW}。创建即 {@code ROOM_PENDING}，等 room-service 回执。</p>
 *
 * <p>{@code (application_id, round_no)} 唯一；{@code interviewer_ids} 存 JSON 字符串数组，
 * 便于详情/日历一次读出，不必额外关联表。</p>
 */
@Data
@TableName("interview")
public class Interview {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private Integer roundNo;

    /** 面试官用户 ID 的 JSON 数组，例如 {@code ["10002","10003"]}。 */
    private String interviewerIds;

    /** 开始时间，UTC。 */
    private LocalDateTime startTime;

    /** 结束时间，UTC。 */
    private LocalDateTime endTime;

    private String mode;

    private String roomType;

    private Long roomId;

    private String status;

    private String cancelReason;

    /** 排期备注（面试须知等）。 */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
