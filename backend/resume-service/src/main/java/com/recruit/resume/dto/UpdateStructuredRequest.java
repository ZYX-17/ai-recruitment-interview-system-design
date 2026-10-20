package com.recruit.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 人工编辑结构化结果。version 用于乐观锁，冲突返回 RSM2004。
 */
public record UpdateStructuredRequest(
        @NotBlank(message = "structuredJson 不能为空")
        String structuredJson,

        @NotNull(message = "version 不能为空")
        Integer version
) {
}
