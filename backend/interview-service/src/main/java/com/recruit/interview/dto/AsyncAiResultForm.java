package com.recruit.interview.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * ai-service 回写逐题转写/评分入参（内部接口）。
 */
@Data
public class AsyncAiResultForm {

    @Size(max = 20000)
    private String transcriptText;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal aiScore;

    /** {@code {dimCode: score}}。 */
    private Map<String, Object> dimensionScores;

    @Size(max = 1024)
    private String comment;

    @NotBlank
    @Size(max = 64)
    private String model;

    @Min(1)
    private Integer promptVersion;
}
