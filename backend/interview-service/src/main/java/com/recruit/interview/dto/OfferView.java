package com.recruit.interview.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Offer 管理端视图（不含 token/hash）。
 *
 * @param id             Offer ID
 * @param offerNo        Offer 编号
 * @param applicationId  投递 ID
 * @param jobId          岗位 ID
 * @param positionTitle  岗位名称快照
 * @param candidateId    候选人 ID
 * @param candidateName  候选人姓名
 * @param salary         薪资
 * @param entryDate      预计入职日期
 * @param expireAt       过期时间
 * @param status         DRAFT/PENDING_APPROVAL/SENT/CONFIRMED/DECLINED/EXPIRED/WITHDRAWN
 * @param onboarding     入职引导
 * @param operatorId     创建人
 * @param confirmedAt    确认时间
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 * @param version        版本号
 */
public record OfferView(
        String id,
        String offerNo,
        String applicationId,
        String jobId,
        String positionTitle,
        String candidateId,
        String candidateName,
        Map<String, Object> salary,
        LocalDate entryDate,
        Instant expireAt,
        String status,
        Map<String, Object> onboarding,
        String operatorId,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer version) {
}
