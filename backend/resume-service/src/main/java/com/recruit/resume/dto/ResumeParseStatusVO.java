package com.recruit.resume.dto;

/**
 * 解析进度（前端轮询接口，契约见 docs/b/05-api.md §5.5）。
 */
public record ResumeParseStatusVO(
        String resumeId,
        String taskNo,
        String parseStatus,
        String stage,
        Integer progress,
        String errorMsg,
        Integer retryCount
) {
}
