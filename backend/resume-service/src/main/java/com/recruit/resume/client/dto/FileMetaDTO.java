package com.recruit.resume.client.dto;

/**
 * file-service {@code GET /internal/files/{id}/meta} 的 data 结构。
 */
public record FileMetaDTO(
        String id,
        String fileKey,
        String fileName,
        String mime,
        long size,
        String bizType,
        String bizId,
        String sha256,
        String status,
        String createdAt,
        String updatedAt
) {
}
