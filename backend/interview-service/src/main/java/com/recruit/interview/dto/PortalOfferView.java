package com.recruit.interview.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * 门户 Offer 视图：只暴露候选人必要字段，不含内部备注/审批意见。
 *
 * @param offerNo        Offer 编号
 * @param positionTitle  岗位名称
 * @param salary         薪资
 * @param entryDate      预计入职日期
 * @param expireAt       过期时间
 * @param status         SENT/CONFIRMED/DECLINED
 * @param candidateName  候选人姓名
 * @param companyName    公司名称
 */
public record PortalOfferView(
        String offerNo,
        String positionTitle,
        Map<String, Object> salary,
        LocalDate entryDate,
        Instant expireAt,
        String status,
        String candidateName,
        String companyName) {
}
