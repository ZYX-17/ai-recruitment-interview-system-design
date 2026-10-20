package com.recruit.resume.dto;

/**
 * Markdown 产物下载地址（预签名，10 分钟有效）。
 */
public record ResumeMarkdownVO(String url, String expireAt) {
}
