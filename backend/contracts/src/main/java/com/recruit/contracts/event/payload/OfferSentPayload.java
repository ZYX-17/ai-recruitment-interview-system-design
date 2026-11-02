package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code offer.sent} 的负载。
 *
 * <p><b>安全红线：</b>不得包含确认 token、confirmUrl 或任何可直接确认 Offer 的凭据。
 * notify 需要链接时调用 interview 内部接口现取。</p>
 *
 * @param offerId       Offer ID
 * @param applicationId 投递 ID
 * @param candidateId   候选人 ID
 * @param offerNo       Offer 编号
 * @param expireAt      过期时间（UTC）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferSentPayload(
        String offerId,
        String applicationId,
        String candidateId,
        String offerNo,
        Instant expireAt) {
}
