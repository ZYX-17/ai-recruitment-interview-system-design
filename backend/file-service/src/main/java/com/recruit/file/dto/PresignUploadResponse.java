package com.recruit.file.dto;

import java.util.Map;

/**
 * 预签名上传结果。
 *
 * @param fileId        文件元数据 ID（字符串，避免 JS 精度问题）
 * @param fileKey       业务文件键，完成上传时回传
 * @param uploadUrl     预签名 PUT URL；秒传时为 null
 * @param method        HTTP 方法，固定 PUT
 * @param headers       建议携带的请求头（如 Content-Type）
 * @param expireAt      URL 过期时间（UTC ISO-8601）；秒传时为 null
 * @param instantUpload true 表示命中秒传，无需再上传，直接用 fileId
 */
public record PresignUploadResponse(
        String fileId,
        String fileKey,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        String expireAt,
        boolean instantUpload
) {
}
