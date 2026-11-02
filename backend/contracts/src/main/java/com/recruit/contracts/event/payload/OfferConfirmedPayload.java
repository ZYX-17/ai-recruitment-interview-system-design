package com.recruit.contracts.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;

/**
 * {@code offer.confirmed} 的负载（候选人确认 Offer）。
 *
 * @param offerId       Offer ID
 * @param applicationId 投递 ID
 * @param candidateId   候选人 ID
 * @param entryDate     预计入职日期
 * @param confirmedAt   确认时间（UTC）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferConfirmedPayload(
        String offerId,
        String applicationId,
        String candidateId,
        LocalDate entryDate,
        Instant confirmedAt) {
}
