package com.recruit.resume.client.dto;

/**
 * ai-service {@code POST /api/ai/v1/extract/resume} 请求体。
 */
public record ExtractResumeRequest(String resumeId, String text) {
}
