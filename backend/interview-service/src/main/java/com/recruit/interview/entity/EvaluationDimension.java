package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评分维度（{@code evaluation_dimension}），随模板版本一起落库。
 */
@Data
@TableName("evaluation_dimension")
public class EvaluationDimension {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long templateId;

    private String dimCode;

    private String dimName;

    private BigDecimal weight;

    private BigDecimal fullScore;

    private BigDecimal vetoThreshold;

    private Integer sort;
}
