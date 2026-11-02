package com.recruit.contracts.event;

import java.util.Map;

/**
 * 事件 Schema 版本表。
 *
 * <p>版本规则（开发文档 §6.1）：字段只增不改；删除字段、改字段语义、改类型都属于破坏性变更，
 * 必须把版本 +1，并让新旧版本至少并行一个迭代。</p>
 *
 * <p>新增事件时必须同时在 {@link EventTypes} 和本表登记，否则
 * {@link #current(String)} 会直接抛错，避免“事件发出去了但消费方不知道版本”。</p>
 */
public final class EventSchemaVersion {

    /** 当前所有事件的初始版本。 */
    public static final int V1 = 1;

    /**
     * 事件类型 -> 当前版本。
     *
     * <p>这里显式列出所有已登记事件，而不是“默认 1”，是为了让漏登记在测试/启动阶段暴露。</p>
     */
    private static final Map<String, Integer> CURRENT = Map.ofEntries(
            Map.entry(EventTypes.JOB_PUBLISHED, V1),
            Map.entry(EventTypes.JOB_UPDATED, V1),

            Map.entry(EventTypes.RESUME_RECEIVED, V1),
            Map.entry(EventTypes.RESUME_PARSE_COMPLETED, V1),
            Map.entry(EventTypes.RESUME_PARSE_FAILED, V1),
            Map.entry(EventTypes.RESUME_PARSED, V1),

            Map.entry(EventTypes.CANDIDATE_DUPLICATED, V1),
            Map.entry(EventTypes.MATCH_COMPLETED, V1),
            Map.entry(EventTypes.TALENT_FOLLOWUP_DUE, V1),

            Map.entry(EventTypes.INTERVIEW_SCHEDULED, V1),
            Map.entry(EventTypes.INTERVIEW_CANCELLED, V1),
            Map.entry(EventTypes.INTERVIEW_RESCHEDULED, V1),
            Map.entry(EventTypes.INTERVIEW_RESULT_SUBMITTED, V1),
            Map.entry(EventTypes.STAGE_CHANGED, V1),

            Map.entry(EventTypes.OFFER_SENT, V1),
            Map.entry(EventTypes.OFFER_CONFIRMED, V1),
            Map.entry(EventTypes.OFFER_DECLINED, V1),
            Map.entry(EventTypes.OFFER_EXPIRED, V1),

            Map.entry(EventTypes.ROOM_CREATED, V1),
            Map.entry(EventTypes.ROOM_CREATE_FAILED, V1),
            Map.entry(EventTypes.ROOM_FINISHED, V1),
            Map.entry(EventTypes.RECORDING_COMPLETED, V1),

            Map.entry(EventTypes.NOTIFICATION_REQUESTED, V1),

            Map.entry(EventTypes.AI_TASK_COMPLETED, V1),
            Map.entry(EventTypes.AI_TASK_FAILED, V1),
            Map.entry(EventTypes.ASR_OVERLOAD, V1)
    );

    private EventSchemaVersion() {
    }

    /**
     * 查询事件当前版本。
     *
     * @param eventType 事件类型
     * @return 版本号
     * @throws IllegalArgumentException 事件未在 {@link EventTypes} 登记时抛出
     */
    public static int current(String eventType) {
        // Map.ofEntries 生成的不可变 Map 在 get(null) 时会抛 NPE，
        // 这里先拦空值，保证调用方拿到的始终是“未登记”这一种语义明确的异常。
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("事件类型不能为空");
        }
        Integer version = CURRENT.get(eventType);
        if (version == null) {
            throw new IllegalArgumentException("事件未登记版本: " + eventType);
        }
        return version;
    }
}
