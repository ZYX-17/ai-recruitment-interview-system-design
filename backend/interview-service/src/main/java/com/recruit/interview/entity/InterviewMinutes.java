package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会议纪要（{@code interview_minutes}）。
 *
 * <p>评价 AI 草稿只读取这里的转写与 AI 要点，不反向写评价；人工确认后才由前端调用评价提交接口。</p>
 */
@Data
@TableName("interview_minutes")
public class InterviewMinutes {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long interviewId;

    /** ai-service 任务 ID（回调幂等）。 */
    private String aiTaskId;

    private String transcriptObjectKey;

    private Integer transcriptSegmentCount;

    private String minutesMd;

    private String aiEvaluation;

    private String reviewStatus;

    /** 确认/驳回意见。 */
    private String reviewComment;

    private Long reviewerId;

    private LocalDateTime reviewedAt;

    private String model;

    private Integer promptVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
