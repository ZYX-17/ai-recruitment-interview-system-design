package com.recruit.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 获取预签名上传 URL 的请求。
 *
 * <p>{@code sha256} 可选：前端/调用方能算就先传，命中已有文件时可直接秒传；
 * 不传则在上传完成后由服务端计算。</p>
 */
public record PresignUploadRequest(

        @NotBlank(message = "fileName 不能为空")
        @Size(max = 255, message = "fileName 过长")
        String fileName,

        @NotBlank(message = "bizType 不能为空")
        @Size(max = 32, message = "bizType 过长")
        String bizType,

        @Size(max = 64, message = "bizId 过长")
        String bizId,

        @NotBlank(message = "mime 不能为空")
        @Size(max = 128, message = "mime 过长")
        String mime,

        @NotNull(message = "size 不能为空")
        @Min(value = 1, message = "size 必须大于 0")
        Long size,

        @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "sha256 必须是 64 位十六进制")
        String sha256
) {
}
