package com.recruit.resume.client.dto;

import java.util.List;

/**
 * ai-worker embedding 响应。
 */
public record EmbedResponse(List<List<Float>> embeddings, Integer dims, String model, String mode) {
}
