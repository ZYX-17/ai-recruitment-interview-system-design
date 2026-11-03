package com.recruit.interview.mapper.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * “我的评价任务”行：面试 + 投递快照 + 当前用户在该轮的评价格子（可能为空）。
 */
@Data
public class MyEvaluationRow {

    private Long interviewId;

    private Long applicationId;

    private Integer roundNo;

    private String interviewerIds;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String interviewStatus;

    private Long jobId;

    private String jobTitle;

    private Long personId;

    private String personName;

    private Long evaluationId;

    private String evaluationStatus;

    private String result;

    private LocalDateTime submittedAt;

    private String dimensionScores;

    private Integer templateVersion;
}
