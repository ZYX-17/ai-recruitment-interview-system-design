package com.recruit.resume.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 简历搜索请求。
 *
 * <p>keyword 为空时退化为纯过滤查询；filters 为空等价于只查已解析简历。</p>
 */
public record ResumeSearchRequest(

        @Size(max = 200, message = "keyword 过长")
        String keyword,

        @Valid
        SearchFilters filters,

        @Min(value = 1, message = "pageNum 从 1 开始")
        Integer pageNum,

        @Min(value = 1, message = "pageSize 必须大于 0")
        @Max(value = 100, message = "pageSize 不能超过 100")
        Integer pageSize
) {
}
