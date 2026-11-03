package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评价重开入参（{@code PUT /api/interview/evaluations/{id}/reopen}）。
 */
@Data
public class EvaluationReopenForm {

    @NotBlank
    @Size(max = 255)
    private String reason;

    @NotNull
    private Integer version;
}
