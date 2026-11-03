package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异步面试试卷（{@code async_paper}）。
 *
 * <p>一份投递一份试卷（{@code application_id} 唯一）；状态机
 * {@code DRAFT → RELEASED → IN_PROGRESS → SUBMITTED → SCORED}，所有流转带 {@code version} CAS。
 * 邀请 token 只存 {@code sha256}，明文只出现在发布响应与邀请链接中。</p>
 */
@Data
@TableName("async_paper")
public class AsyncPaper {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String paperNo;

    private Long applicationId;

    /** DRAFT / RELEASED / IN_PROGRESS / SUBMITTED / SCORED。 */
    private String status;

    private Integer questionCount;

    private Integer durationLimitSec;

    /** 拍摄要求/防作弊开关等 JSON。 */
    private String settingsJson;

    /** sha256(邀请 token)，明文不落库。 */
    private String inviteTokenHash;

    private LocalDateTime inviteExpireAt;

    private LocalDateTime startedAt;

    private LocalDateTime submittedAt;

    private BigDecimal totalScore;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
