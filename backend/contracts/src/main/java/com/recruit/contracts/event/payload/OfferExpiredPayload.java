package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code offer.expired} 的负载（Offer 到期未确认）。
 *
 * @param offerId       Offer ID
 * @param applicationId 投递 ID
 * @param candidateId   候选人 ID
 * @param expiredAt     过期时间（UTC）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferExpiredPayload(
        String offerId,
        String applicationId,
        String candidateId,
        Instant expiredAt) {
}
