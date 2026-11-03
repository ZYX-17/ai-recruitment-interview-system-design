package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code candidate.duplicated} 的负载（resume → notify）。
 *
 * @param personId          主档案 ID
 * @param duplicatePersonIds 重复候选人 ID
 * @param similarity        相似度
 * @param dedupType         查重类型
 * @param receiverId        接收人用户 ID（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandidateDuplicatedPayload(
        String personId,
        List<String> duplicatePersonIds,
        Double similarity,
        String dedupType,
        String receiverId) {
}
