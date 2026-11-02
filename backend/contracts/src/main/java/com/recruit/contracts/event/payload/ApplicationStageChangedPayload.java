package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code application.stage.changed} 的负载（投递阶段变化）。
 *
 * @param applicationId 投递 ID
 * @param fromStage     原阶段
 * @param toStage       新阶段
 * @param roundNo       轮次，可为空
 * @param result        阶段结果，可为空
 * @param operatorId    操作人用户 ID
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationStageChangedPayload(
        String applicationId,
        String fromStage,
        String toStage,
        Integer roundNo,
        String result,
        String operatorId) {
}
