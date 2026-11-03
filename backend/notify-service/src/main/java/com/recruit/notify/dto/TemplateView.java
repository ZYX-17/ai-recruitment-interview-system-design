package com.recruit.notify.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知模板展示。
 */
public record TemplateView(
        String id,
        String templateCode,
        String channel,
        int version,
        String titleTemplate,
        String contentTemplate,
        List<String> variables,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
