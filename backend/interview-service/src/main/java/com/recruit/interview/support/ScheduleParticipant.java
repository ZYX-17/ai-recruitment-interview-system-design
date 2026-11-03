package com.recruit.interview.support;

/**
 * 排期参与者：面试官 / 候选人 / 资源（会议室、设备等）。
 *
 * <p>数据库唯一键 {@code (participant_type, participant_id, slot_date, slot_no)}
 * 保证同一参与者在同一格只能有一个面试，是并发冲突的最终兜底。</p>
 *
 * @param participantType 参与者类型
 * @param participantId   参与者 ID
 */
public record ScheduleParticipant(String participantType, long participantId) {

    public static final String INTERVIEWER = "INTERVIEWER";
    public static final String CANDIDATE = "CANDIDATE";
    public static final String RESOURCE = "RESOURCE";
}
