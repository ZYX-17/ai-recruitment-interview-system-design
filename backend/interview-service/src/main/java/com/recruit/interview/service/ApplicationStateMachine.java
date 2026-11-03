package com.recruit.interview.service;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.StageDef;
import com.recruit.interview.support.ApplicationActionPermission;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 投递状态机（docs/4 §7.4.1）。
 *
 * <p>状态与阶段必须一起更新；所有合法事件集中在本类，Controller/Service 不允许直接写状态字符串。
 * 状态级流转用显式 {@link Map}，阶段推进由 {@code stage_flow} 决定。</p>
 */
@Component
public class ApplicationStateMachine {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PASSED = "PASSED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String TRANSFERRED = "TRANSFERRED";
    public static final String OFFERED = "OFFERED";
    public static final String HIRED = "HIRED";

    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String RESULT_PENDING = "PENDING";

    /** 状态 -> 允许的动作。终态不在 Map 中，任何动作都会被拒绝。 */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            IN_PROGRESS, Set.of(
                    ApplicationActionPermission.ACTION_ADVANCE,
                    ApplicationActionPermission.ACTION_PASS,
                    ApplicationActionPermission.ACTION_REJECT,
                    ApplicationActionPermission.ACTION_HOLD,
                    ApplicationActionPermission.ACTION_TRANSFER,
                    ApplicationActionPermission.ACTION_WITHDRAW,
                    ApplicationActionPermission.ACTION_HIRE),
            OFFERED, Set.of(
                    ApplicationActionPermission.ACTION_HIRE,
                    ApplicationActionPermission.ACTION_REJECT,
                    ApplicationActionPermission.ACTION_WITHDRAW),
            PASSED, Set.of(
                    ApplicationActionPermission.ACTION_HIRE,
                    ApplicationActionPermission.ACTION_WITHDRAW));

    /**
     * 计算动作后的状态/阶段。
     *
     * <p>本方法只做纯计算，不写库；调用方负责版本乐观锁、历史与 Outbox。</p>
     *
     * @param currentStatus 当前状态
     * @param currentStage  当前阶段
     * @param action        动作
     * @param targetStage   目标阶段（ADVANCE/PASS 可选）
     * @param stageFlow     投递复制来的阶段配置
     * @return 目标状态/阶段/结果
     * @throws BizException 流转非法时抛 409 ITV3102
     */
    public TransitionResult apply(String currentStatus, String currentStage, String action,
                                  String targetStage, List<StageDef> stageFlow) {
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
        Set<String> allowed = ALLOWED.get(currentStatus);
        if (allowed == null || !allowed.contains(normalizedAction)) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    "状态流转非法：当前状态 " + currentStatus + " 不允许执行 " + normalizedAction);
        }

        return switch (normalizedAction) {
            case ApplicationActionPermission.ACTION_ADVANCE,
                 ApplicationActionPermission.ACTION_PASS -> advance(currentStage, targetStage, stageFlow);
            case ApplicationActionPermission.ACTION_HOLD ->
                    new TransitionResult(IN_PROGRESS, currentStage, 0, RESULT_PENDING);
            case ApplicationActionPermission.ACTION_REJECT ->
                    new TransitionResult(REJECTED, currentStage, 0, RESULT_FAIL);
            case ApplicationActionPermission.ACTION_WITHDRAW ->
                    new TransitionResult(WITHDRAWN, currentStage, 0, null);
            case ApplicationActionPermission.ACTION_TRANSFER ->
                    new TransitionResult(TRANSFERRED, currentStage, 0, null);
            case ApplicationActionPermission.ACTION_HIRE ->
                    new TransitionResult(HIRED, currentStage, 0, RESULT_PASS);
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "不支持的投递动作: " + action);
        };
    }

    /**
     * 是否是终态（不可再推进）。
     *
     * @param status 状态
     * @return 终态返回 true
     */
    public boolean isTerminal(String status) {
        return !ALLOWED.containsKey(status);
    }

    private TransitionResult advance(String currentStage, String targetStage, List<StageDef> stageFlow) {
        if (stageFlow == null || stageFlow.isEmpty()) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION, "投递缺少流程配置，无法推进");
        }
        int currentIndex = indexOf(stageFlow, currentStage);
        if (currentIndex < 0) {
            throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                    "状态流转非法：当前阶段 " + currentStage + " 不在流程中");
        }
        int nextIndex = currentIndex + 1;
        if (targetStage != null && !targetStage.isBlank()) {
            nextIndex = indexOf(stageFlow, targetStage.trim());
            if (nextIndex <= currentIndex) {
                throw new BizException(ErrorCode.ILLEGAL_TRANSITION,
                        "状态流转非法：目标阶段必须在当前阶段之后");
            }
        }
        if (nextIndex >= stageFlow.size()) {
            return new TransitionResult(PASSED, currentStage, 0, RESULT_PASS);
        }
        StageDef next = stageFlow.get(nextIndex);
        return new TransitionResult(IN_PROGRESS, next.getStageNo(), 0, RESULT_PASS);
    }

    private static int indexOf(List<StageDef> stageFlow, String stageNo) {
        if (stageNo == null) {
            return -1;
        }
        for (int i = 0; i < stageFlow.size(); i++) {
            if (stageNo.equals(stageFlow.get(i).getStageNo())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 状态机计算结果。
     *
     * @param status   目标状态
     * @param stageNo  目标阶段
     * @param roundNo  目标轮次
     * @param result   阶段结果 PASS/FAIL/PENDING
     */
    public record TransitionResult(String status, String stageNo, int roundNo, String result) {
    }
}
