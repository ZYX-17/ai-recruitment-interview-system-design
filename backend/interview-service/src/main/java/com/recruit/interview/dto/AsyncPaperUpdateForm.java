package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 修改 DRAFT 试卷入参（{@code PUT /api/interview/async-interviews/{id}}）。
 *
 * <p>字段为 null 表示保持原值；{@code version} 必填用于 CAS。</p>
 */
@Data
public class AsyncPaperUpdateForm {

    @NotNull
    private Integer version;

    @Min(0)
    @Max(50)
    private Integer questionCount;

    @Min(60)
    @Max(14400)
    private Integer durationLimitSec;

    private Map<String, Object> settings;
}
