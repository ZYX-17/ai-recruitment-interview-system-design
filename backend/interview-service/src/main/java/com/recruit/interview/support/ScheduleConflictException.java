package com.recruit.interview.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.ConflictItemView;

import java.util.List;

/**
 * 排期冲突异常：除 409 {@code ITV3101} 文案外，还携带结构化冲突明细。
 *
 * <p>{@link BizException} 只有 message，无法把“谁/哪一格/哪场面试”返回给前端；
 * 这里用子类带上明细，由 {@link ScheduleConflictAdvice} 写进响应 {@code data}。</p>
 */
public class ScheduleConflictException extends BizException {

    private final List<ConflictItemView> conflicts;

    /**
     * 由冲突明细构造。
     *
     * @param conflicts 冲突明细，至少 1 条
     */
    public ScheduleConflictException(List<ConflictItemView> conflicts) {
        super(ErrorCode.SCHEDULE_CONFLICT, buildMessage(conflicts));
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    /**
     * 冲突明细。
     *
     * @return 只读明细
     */
    public List<ConflictItemView> getConflicts() {
        return conflicts;
    }

    private static String buildMessage(List<ConflictItemView> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return ErrorCode.SCHEDULE_CONFLICT.getDefaultMessage();
        }
        ConflictItemView first = conflicts.get(0);
        return String.format("排期冲突：%s 在 %s 第 %d 格已被面试 %s 占用，共 %d 处冲突",
                first.participantName(), first.slotDate(), first.slotNo(),
                first.existingInterviewId(), conflicts.size());
    }
}
