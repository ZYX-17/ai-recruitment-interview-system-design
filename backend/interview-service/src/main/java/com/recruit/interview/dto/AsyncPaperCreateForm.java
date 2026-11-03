package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 创建异步试卷入参（{@code POST /api/interview/async-interviews}）。
 */
@Data
public class AsyncPaperCreateForm {

    @NotBlank
    private String applicationId;

    /** 期望题目数（提示值）；发布时以题目表实际条数为准。 */
    @Min(0)
    @Max(50)
    private Integer questionCount;

    @Min(60)
    @Max(14400)
    private Integer durationLimitSec;

    /** 拍摄要求/防作弊开关。 */
    private Map<String, Object> settings;
}
