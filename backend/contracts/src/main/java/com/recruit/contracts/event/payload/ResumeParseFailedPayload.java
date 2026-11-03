package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code resume.parse.failed} 的负载（resume → notify）。
 *
 * <p>{@code receiverId} 为 B 域补充的可选字段：用于指定需要接收解析失败提醒的 HR。</p>
 *
 * @param resumeId   简历 ID
 * @param taskNo     解析任务号
 * @param reason     失败原因
 * @param attempt    第几次尝试
 * @param receiverId 接收人用户 ID（可选）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResumeParseFailedPayload(
        String resumeId,
        String taskNo,
        String reason,
        Integer attempt,
        String receiverId) {
}
