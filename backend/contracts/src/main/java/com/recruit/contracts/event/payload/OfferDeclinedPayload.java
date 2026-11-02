package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code offer.declined} 的负载（候选人拒绝 Offer）。
 *
 * @param offerId       Offer ID
 * @param applicationId 投递 ID
 * @param candidateId   候选人 ID
 * @param reason        拒绝原因，可为空
 * @param declinedAt    拒绝时间（UTC）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferDeclinedPayload(
        String offerId,
        String applicationId,
        String candidateId,
        String reason,
        Instant declinedAt) {
}
