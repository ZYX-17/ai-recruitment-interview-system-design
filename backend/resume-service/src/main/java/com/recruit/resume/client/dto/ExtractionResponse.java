package com.recruit.resume.client.dto;

import java.util.Map;

/**
 * 简历实体抽取结果（AI 草稿）。
 */
public record ExtractionResponse(
        Map<String, Object> structured,
        String provider,
        String model,
        Integer promptVersion,
        Long latencyMs,
        Boolean repaired
) {
}
