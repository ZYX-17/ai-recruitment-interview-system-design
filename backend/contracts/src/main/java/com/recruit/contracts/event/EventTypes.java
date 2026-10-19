package com.recruit.contracts.event;

/**
 * 事件类型清单（开发文档 5.2 登记 15 个）。新增事件先登记文档再写代码。
 */
public final class EventTypes {

    private EventTypes() {
    }

    // 岗位（interview → resume 快照同步）
    public static final String JOB_PUBLISHED = "job.published";
    public static final String JOB_UPDATED = "job.updated";

    // 简历与匹配
    public static final String RESUME_PARSED = "resume.parsed";
    public static final String MATCH_COMPLETED = "match.completed";

    // 面试流程
    public static final String INTERVIEW_SCHEDULED = "interview.scheduled";   // room 监听建房
    public static final String INTERVIEW_CANCELLED = "interview.cancelled";
    public static final String STAGE_CHANGED = "application.stage.changed";
    public static final String OFFER_SENT = "offer.sent";

    // 在线面试（room 域）
    public static final String ROOM_FINISHED = "room.finished";               // 触发纪要（主触发）
    public static final String RECORDING_COMPLETED = "recording.completed";   // 录像登记/可选补转写

    // 通知
    public static final String NOTIFICATION_REQUESTED = "notification.requested";

    // AI
    public static final String AI_TASK_COMPLETED = "ai_task.completed";
    public static final String ASR_OVERLOAD = "asr.overload";                 // 超容量降级通知
}
