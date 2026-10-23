package com.recruit.resume.client.dto;

import java.util.List;

/**
 * ai-worker {@code POST /api/v1/embed} 请求体。
 */
public record EmbedRequest(List<String> texts) {
}
