package com.recruit.file.dto;

import com.recruit.file.entity.FileMeta;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 文件元数据对外视图。
 *
 * <p>{@code id} 用字符串返回：后端 BIGINT 雪花 ID 超出 JS Number 安全整数范围，
 * 直接返回数字会在前端丢精度。时间统一转 UTC ISO-8601。</p>
 */
public record FileMetaVO(
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

    public static FileMetaVO from(FileMeta meta) {
        return new FileMetaVO(
                meta.getId() == null ? null : String.valueOf(meta.getId()),
                meta.getFileKey(),
                meta.getFileName(),
                meta.getMime(),
                meta.getSize() == null ? 0L : meta.getSize(),
                meta.getBizType(),
                meta.getBizId(),
                meta.getSha256(),
                meta.getStatus(),
                utc(meta.getCreatedAt()),
                utc(meta.getUpdatedAt()));
    }

    private static String utc(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC).toString();
    }
}
