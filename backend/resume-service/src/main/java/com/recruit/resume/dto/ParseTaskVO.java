package com.recruit.resume.dto;

import com.recruit.resume.entity.ResumeParseTask;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 解析任务视图。
 */
public record ParseTaskVO(
        String id,
        String taskNo,
        String resumeId,
        String aiTaskNo,
        String status,
        String stage,
        Integer progress,
        String errorMsg,
        Integer retryCount,
        String createdAt,
        String updatedAt
) {

    public static ParseTaskVO from(ResumeParseTask task) {
        return new ParseTaskVO(
                String.valueOf(task.getId()),
                task.getTaskNo(),
                String.valueOf(task.getResumeId()),
                task.getAiTaskNo(),
                task.getStatus(),
                task.getStage(),
                task.getProgress(),
                task.getErrorMsg(),
                task.getRetryCount(),
                utc(task.getCreatedAt()),
                utc(task.getUpdatedAt()));
    }

    private static String utc(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC).toString();
    }
}
