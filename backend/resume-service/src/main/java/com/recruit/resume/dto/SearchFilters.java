package com.recruit.resume.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 简历搜索过滤条件（对应 docs/b/05-api.md §5.5）。
 *
 * <p>parseStatus 为空时默认只搜 SUCCESS；传 ALL 表示不限解析状态。</p>
 */
public record SearchFilters(

        @Size(max = 64, message = "city 过长")
        String city,

        @Size(max = 32, message = "education 过长")
        String education,

        @Min(value = 0, message = "workYearsMin 不能为负")
        Integer workYearsMin,

        @Min(value = 0, message = "workYearsMax 不能为负")
        @Max(value = 60, message = "workYearsMax 不合理")
        Integer workYearsMax,

        List<@Size(max = 64, message = "skill 过长") String> skills,

        @Size(max = 16, message = "parseStatus 过长")
        String parseStatus
) {
}
