package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer（{@code offer}）。
 *
 * <p>一个投递只有一份 Offer；确认走免登录 token，库中只存 {@code sha256(token)}。
 * 状态机：{@code DRAFT → PENDING_APPROVAL → SENT → CONFIRMED/DECLINED/EXPIRED/WITHDRAWN}，
 * 所有流转都带 {@code version} CAS。</p>
 */
@Data
@TableName("offer")
public class Offer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String offerNo;

    private Long applicationId;

    /** 岗位名称快照。 */
    private String positionTitle;

    /** 薪资 JSON。 */
    private String salaryJson;

    private LocalDate entryDate;

    private LocalDateTime expireAt;

    /** sha256(token) 十六进制，明文不落库。 */
    private String confirmTokenHash;

    private String status;

    private String onboardingJson;

    private Long operatorId;

    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
