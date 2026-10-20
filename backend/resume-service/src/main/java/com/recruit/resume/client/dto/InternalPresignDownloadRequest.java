package com.recruit.resume.client.dto;

/**
 * file-service {@code POST /internal/files/presign/download} 请求体。
 */
public record InternalPresignDownloadRequest(Long fileId) {
}
