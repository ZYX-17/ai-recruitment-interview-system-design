package com.recruit.file.dto;

/**
 * 下载地址（预签名 GET，默认 10 分钟有效）。
 *
 * @param fileId   文件 ID（字符串）
 * @param url      预签名下载 URL
 * @param expireAt URL 过期时间（UTC ISO-8601）
 */
public record DownloadUrlResponse(String fileId, String url, String expireAt) {
}
