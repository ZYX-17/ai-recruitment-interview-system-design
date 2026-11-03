package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 评价提交入参（{@code POST /api/interview/schedules/{id}/evaluations}）。
 *
 * <p>{@code dimensionScores} 必须覆盖模板全部维度；{@code templateVersion} 必须与投递岗位的
 * 模板版本对应；新提交 {@code version=0}，重开后的重提使用当前行版本。</p>
 */
@Data
public class EvaluationSubmitForm {

    @NotEmpty
    private Map<String, BigDecimal> dimensionScores;

    @Size(max = 1024)
    private String comment;

    @NotBlank
    @Size(max = 16)
    private String result;

    @NotNull
    private Integer templateVersion;

    @NotNull
    private Integer version;
}
