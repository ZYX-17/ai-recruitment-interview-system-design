package com.recruit.interview.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 人工复核入参。
 */
@Data
public class AsyncReviewForm {

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal manualScore;

    @Size(max = 1024)
    private String comment;

    @NotNull
    private Integer version;
}
