package com.recruit.resume.client.dto;

/**
 * file-service 预签名下载响应：{@code {fileId,url,expireAt}}。
 */
public record DownloadUrlDTO(String fileId, String url, String expireAt) {
}
