package com.recruit.interview.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 评分维度定义（模板 {@code score_dims} 的元素）。
 */
@Data
public class ScoreDim {

    @NotBlank
    @Size(max = 32)
    private String dimCode;

    @NotBlank
    @Size(max = 64)
    private String dimName;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal weight;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal fullScore;

    private BigDecimal vetoThreshold;
}
