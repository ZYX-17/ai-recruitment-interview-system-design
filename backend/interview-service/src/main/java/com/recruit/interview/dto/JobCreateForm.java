package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 新增岗位入参（{@code POST /api/interview/jobs}）。
 *
 * <p>ID 一律用字符串，避免前端 JS 精度丢失；{@code requirements} 原样落 JSON。</p>
 */
@Data
public class JobCreateForm {

    @NotBlank
    @Size(max = 64)
    private String jobCode;

    @NotBlank
    @Size(max = 128)
    private String title;

    private String deptId;

    @Size(max = 64)
    private String deptName;

    private String jdText;

    private Map<String, Object> requirements;

    @Min(1)
    @Max(100000)
    private Integer headcount;

    @Size(max = 64)
    private String salaryRange;

    private String ownerHrId;
}
