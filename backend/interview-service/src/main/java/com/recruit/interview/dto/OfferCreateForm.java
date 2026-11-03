package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * 创建 Offer 入参（{@code POST /api/interview/offers}）。
 */
@Data
public class OfferCreateForm {

    @NotBlank
    private String applicationId;

    private Map<String, Object> salary;

    private LocalDate entryDate;

    @NotNull
    private Instant expireAt;

    private Map<String, Object> onboarding;

    @Size(max = 255)
    private String remark;
}
