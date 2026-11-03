package com.recruit.interview.mapper.row;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异步作答行：作答字段 + 题目快照（题目文本、序号、权重）。
 */
@Data
public class AsyncAnswerRow {

    private Long id;
    private Long questionId;
    private Long candidateId;
    private Long videoFileId;
    private Long audioFileId;
    private Integer answerSec;
    private String transcriptText;
    private BigDecimal aiScore;
    private BigDecimal manualScore;
    private String manualComment;
    private Long manualScoredBy;
    private LocalDateTime manualScoredAt;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;

    private Long paperId;
    private Integer seqNo;
    private String questionText;
    private BigDecimal weight;
}
