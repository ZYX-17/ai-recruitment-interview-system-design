package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异步面试作答（{@code async_answer}）。
 *
 * <p>{@code question_id} 唯一：一题一份作答。视频/音频只存 file-service 文件 ID；
 * 提交后不可重答（HR 重置需审计）。</p>
 */
@Data
@TableName("async_answer")
public class AsyncAnswer {

    @TableId(type = IdType.ASSIGN_ID)
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

    /** PENDING / SUBMITTED / TRANSCRIBED / AI_SCORED / REVIEWED。 */
    private String status;

    private LocalDateTime submittedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
