package com.recruit.interview.mapper.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异步试卷列表/详情行：试卷字段 + 投递/岗位快照与数据范围字段。
 */
@Data
public class AsyncPaperRow {

    private Long id;
    private String paperNo;
    private Long applicationId;
    private String status;
    private Integer questionCount;
    private Integer durationLimitSec;
    private String settingsJson;
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

    private Long jobId;
    private String jobTitle;
    private Long personId;
    private String personName;
    private Long deptId;
    private Long ownerHrId;
}
