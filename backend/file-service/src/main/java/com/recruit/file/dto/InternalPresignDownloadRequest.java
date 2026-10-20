package com.recruit.file.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 服务间获取下载地址：按 fileId 预签名。
 */
public record InternalPresignDownloadRequest(
        @NotNull(message = "fileId 不能为空")
        Long fileId
) {
}
