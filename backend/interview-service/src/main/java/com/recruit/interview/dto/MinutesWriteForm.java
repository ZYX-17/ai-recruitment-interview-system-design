package com.recruit.interview.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * ai-service 回写纪要入参（内部接口 X-02）。
 */
@Data
public class MinutesWriteForm {

    @NotBlank
    @Size(max = 64)
    private String taskId;

    @Size(max = 255)
    private String transcriptObjectKey;

    @Min(0)
    private Integer transcriptSegmentCount;

    private String minutesMd;

    /** 要点/风险/建议等结构化结果。 */
    private Map<String, Object> aiEvaluation;

    @Size(max = 64)
    private String model;

    @Min(1)
    private Integer promptVersion;
}
