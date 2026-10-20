package com.recruit.contracts.event;

/**
 * 事件类型清单（与《开发文档》5.2 对齐；新增事件先登记文档再写代码）。
 *
 * <p>命名口径（B 域）：
 * <ul>
 *   <li>简历：{@code resume.received} / {@code resume.parse.completed} / {@code resume.parse.failed}</li>
 *   <li>查重：{@code candidate.duplicated}</li>
 *   <li>匹配：{@code match.completed}（B 域扩展，已在开发文档登记）</li>
 *   <li>人才：{@code talent.followup.due}</li>
 *   <li>AI：{@code ai.task.completed} / {@code ai.task.failed}</li>
 *   <li>面试/Offer：{@code interview.result.submitted} / {@code offer.confirmed}</li>
 * </ul>
 *
 * <p>历史常量 {@link #RESUME_PARSED} 保留兼容，新代码使用 {@link #RESUME_PARSE_COMPLETED}。
 */
public final class EventTypes {

    private EventTypes() {
    }

    // 岗位（interview → resume 快照同步）
    public static final String JOB_PUBLISHED = "job.published";
    public static final String JOB_UPDATED = "job.updated";

    // 简历解析（B 域）
    public static final String RESUME_RECEIVED = "resume.received";
    public static final String RESUME_PARSE_COMPLETED = "resume.parse.completed";
    public static final String RESUME_PARSE_FAILED = "resume.parse.failed";

    /** @deprecated 旧骨架常量，保留兼容；新代码使用 {@link #RESUME_PARSE_COMPLETED}。 */
    @Deprecated
    public static final String RESUME_PARSED = "resume.parsed";

    // 查重/匹配（B 域）
    public static final String CANDIDATE_DUPLICATED = "candidate.duplicated";
    public static final String MATCH_COMPLETED = "match.completed";

    // 人才库（B 域）
    public static final String TALENT_FOLLOWUP_DUE = "talent.followup.due";

    // 面试流程（A/C 域）
    public static final String INTERVIEW_SCHEDULED = "interview.scheduled";   // room 监听建房
    // 2026-10-19 已与总开发文档对齐为 interview.cancelled（dev ea35b16）。
    public static final String INTERVIEW_CANCELLED = "interview.cancelled";
    public static final String INTERVIEW_RESULT_SUBMITTED = "interview.result.submitted";
    public static final String STAGE_CHANGED = "application.stage.changed";
    public static final String OFFER_SENT = "offer.sent";
    public static final String OFFER_CONFIRMED = "offer.confirmed";

    // 在线面试（room 域）
    public static final String ROOM_CREATED = "room.created";                 // 建房回执（interview 回填 room_id）
    public static final String ROOM_CREATE_FAILED = "room.create.failed";     // 建房失败（重试或人工处理）
    public static final String ROOM_FINISHED = "room.finished";               // 触发纪要（主触发）
    public static final String RECORDING_COMPLETED = "recording.completed";   // 录像登记/可选补转写

    // 通知
    public static final String NOTIFICATION_REQUESTED = "notification.requested";

    // AI
    public static final String AI_TASK_COMPLETED = "ai.task.completed";
    public static final String AI_TASK_FAILED = "ai.task.failed";
    public static final String ASR_OVERLOAD = "asr.overload";                 // 超容量降级通知
}
