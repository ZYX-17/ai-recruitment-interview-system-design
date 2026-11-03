package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阶段流转历史（{@code application_stage_history}），只增不改。
 */
@Data
@TableName("application_stage_history")
public class ApplicationStageHistory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long applicationId;

    private String stageNo;

    private Integer roundNo;

    private String status;

    /** PASS / FAIL / PENDING。 */
    private String result;

    private Long operatorId;

    private String comment;

    private LocalDateTime operatedAt;

    private LocalDateTime createdAt;
}
