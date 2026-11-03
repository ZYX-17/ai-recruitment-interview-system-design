package com.recruit.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 纪要人工确认/驳回入参（X-05）。
 */
@Data
public class MinutesReviewForm {

    @NotNull
    @Pattern(regexp = "CONFIRM|REJECT", message = "decision 只能是 CONFIRM/REJECT")
    private String decision;

    @Size(max = 1024)
    private String comment;

    @NotNull
    private Integer version;
}
