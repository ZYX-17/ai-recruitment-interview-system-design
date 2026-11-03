package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试评价（{@code evaluation}）。
 *
 * <p>唯一键 {@code (interview_id, round_no, interviewer_id)} 保证一名面试官一轮只有一份。
 * 提交后不可改；HR_ADMIN 重开时记录 {@code reopened_by/reopened_at/reopen_reason}，
 * 原面试官重提后状态回到 {@code SUBMITTED}。</p>
 */
@Data
@TableName("evaluation")
public class Evaluation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long interviewId;

    private Integer roundNo;

    private Long interviewerId;

    /** {@code {dimCode: score}}。 */
    private String dimensionScores;

    private String comment;

    private String result;

    /** SUBMITTED / REOPENED。 */
    private String status;

    /** 提交时对应的模板版本。 */
    private Integer templateVersion;

    private LocalDateTime submittedAt;

    private Long reopenedBy;

    private LocalDateTime reopenedAt;

    private String reopenReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
