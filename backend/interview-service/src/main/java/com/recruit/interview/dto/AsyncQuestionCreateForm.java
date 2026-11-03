package com.recruit.interview.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增题目入参（{@code POST /api/interview/async-interviews/{id}/questions}）。
 */
@Data
public class AsyncQuestionCreateForm {

    @NotNull
    @Min(1)
    @Max(999)
    private Integer seqNo;

    @NotBlank
    @Size(max = 2000)
    private String questionText;

    @Min(0)
    @Max(600)
    private Integer prepareSec;

    @Min(10)
    @Max(1800)
    private Integer answerSec;

    @DecimalMin("0.01")
    @DecimalMax("100.00")
    private BigDecimal weight;
}
