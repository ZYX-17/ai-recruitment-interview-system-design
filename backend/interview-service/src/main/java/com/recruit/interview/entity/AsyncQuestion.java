package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异步面试题目（{@code async_question}）。
 *
 * <p>{@code (paper_id, seq_no)} 唯一；发布后题目只读，仅 DRAFT 可增题。</p>
 */
@Data
@TableName("async_question")
public class AsyncQuestion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long paperId;

    private Integer seqNo;

    private String questionText;

    /** 读题准备时长（秒）。 */
    private Integer prepareSec;

    /** 作答时长上限（秒）。 */
    private Integer answerSec;

    private BigDecimal weight;

    private LocalDateTime createdAt;
}
