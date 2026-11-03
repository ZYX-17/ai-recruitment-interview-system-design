package com.recruit.interview.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增投递入参（{@code POST /api/interview/applications}）。
 *
 * <p>{@code personName} 为简历库姓名快照；跨域取不到时允许省略，服务端用占位名兜底。</p>
 */
@Data
public class ApplicationCreateForm {

    @NotBlank
    private String jobId;

    @NotBlank
    private String personId;

    @Size(max = 64)
    private String personName;

    @Size(max = 32)
    private String source;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal matchScore;
}
