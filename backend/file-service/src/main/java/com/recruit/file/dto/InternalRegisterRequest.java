package com.recruit.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 服务间文件登记：对象已经写入 MinIO（例如 ai-service 上传解析产物），
 * 由调用方把元数据登记到 file_meta。
 */
public record InternalRegisterRequest(

        @Size(max = 64, message = "fileKey 过长")
        String fileKey,

        @Size(max = 64, message = "bucket 过长")
        String bucket,

        @NotBlank(message = "objectKey 不能为空")
        @Size(max = 255, message = "objectKey 过长")
        String objectKey,

        @NotBlank(message = "sha256 不能为空")
        @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "sha256 必须是 64 位十六进制")
        String sha256,

        @NotBlank(message = "mime 不能为空")
        @Size(max = 128, message = "mime 过长")
        String mime,

        @NotNull(message = "size 不能为空")
        @Min(value = 1, message = "size 必须大于 0")
        Long size,

        @NotBlank(message = "bizType 不能为空")
        @Size(max = 32, message = "bizType 过长")
        String bizType,

        @Size(max = 64, message = "bizId 过长")
        String bizId,

        @NotBlank(message = "fileName 不能为空")
        @Size(max = 255, message = "fileName 过长")
        String fileName,

        @NotNull(message = "uploaderId 不能为空")
        Long uploaderId
) {
}
