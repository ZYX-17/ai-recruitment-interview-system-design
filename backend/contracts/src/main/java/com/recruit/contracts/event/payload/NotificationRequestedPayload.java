package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * {@code notification.requested} 的负载（各服务 → notify）。
 *
 * <p>模板变量只放渲染所需的最小集合；禁止放密码、令牌、完整手机号、简历全文。</p>
 *
 * @param templateCode  模板编码
 * @param receiverId    接收人用户 ID
 * @param receiverEmail 接收邮箱，可为空（站内信不需要）
 * @param channel       渠道列表，例如 EMAIL / INAPP / SMS_MOCK
 * @param bizType       业务类型，例如 INTERVIEW_INVITE
 * @param bizId         业务 ID
 * @param variables     模板变量
 * @param dedupKey      去重键，可为空
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationRequestedPayload(
        String templateCode,
        String receiverId,
        String receiverEmail,
        List<String> channel,
        String bizType,
        String bizId,
        Map<String, Object> variables,
        String dedupKey) {
}
