package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步作答 AI 评分明细（{@code async_answer_score}）。
 *
 * <p>{@code answer_id} 唯一；保留模型与 Prompt 版本，便于回溯评分口径。</p>
 */
@Data
@TableName("async_answer_score")
public class AsyncAnswerScore {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long answerId;

    /** {@code {dimCode: score}}。 */
    private String dimensionScores;

    private String comment;

    private String model;

    private Integer promptVersion;

    private LocalDateTime createdAt;
}
