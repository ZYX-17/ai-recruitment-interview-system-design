package com.recruit.file.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 服务间完成上传。文件 ID 在路径上，MIME/大小/文件名以 file_meta 登记值为准；
 * 这里只允许补传 sha256 做一致性校验。
 */
public record InternalCompleteRequest(
        @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "sha256 必须是 64 位十六进制")
        String sha256
) {
}
