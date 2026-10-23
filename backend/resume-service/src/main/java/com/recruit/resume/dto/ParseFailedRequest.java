package com.recruit.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * mineru-worker 解析失败回调。
 */
public record ParseFailedRequest(

        @NotBlank(message = "taskNo 不能为空")
        @Size(max = 64, message = "taskNo 过长")
        String taskNo,

        @NotBlank(message = "reason 不能为空")
        @Size(max = 512, message = "reason 过长")
        String reason
) {
}
