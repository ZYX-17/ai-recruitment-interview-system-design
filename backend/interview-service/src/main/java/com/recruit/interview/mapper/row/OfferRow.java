package com.recruit.interview.mapper.row;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer 列表/详情行：offer 主表 + 投递/岗位快照（用于候选人展示与数据范围判断）。
 */
@Data
public class OfferRow {

    private Long id;

    private String offerNo;

    private Long applicationId;

    private String positionTitle;

    private String salaryJson;

    private LocalDate entryDate;

    private LocalDateTime expireAt;

    private String status;

    private String onboardingJson;

    private Long operatorId;

    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Long jobId;

    private Long personId;

    private String personName;

    private Long deptId;

    private Long ownerHrId;
}
