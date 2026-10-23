package com.recruit.resume.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * mineru-worker 解析成功回调。
 *
 * <p>Markdown 已由 worker 上传到 file-service，这里只带着产物 fileId 和解析器版本回写。</p>
 */
public record ParseCompletedRequest(

        @NotBlank(message = "taskNo 不能为空")
        @Size(max = 64, message = "taskNo 过长")
        String taskNo,

        @NotNull(message = "markdownFileId 不能为空")
        Long markdownFileId,

        @NotBlank(message = "parserVersion 不能为空")
        @Size(max = 32, message = "parserVersion 过长")
        String parserVersion,

        @Min(value = 0, message = "chunkCount 不能为负")
        Integer chunkCount
) {
}
