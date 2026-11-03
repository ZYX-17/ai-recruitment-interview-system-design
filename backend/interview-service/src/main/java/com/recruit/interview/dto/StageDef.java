package com.recruit.interview.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流程阶段定义（模板 {@code stage_flow} 的元素）。
 */
@Data
public class StageDef {

    @NotBlank
    @Size(max = 32)
    private String stageNo;

    @NotBlank
    @Size(max = 64)
    private String stageName;

    /** INTERNAL / ASYNC / ONLINE / OFFER。 */
    @NotBlank
    @Size(max = 16)
    private String mode;

    @Min(0)
    private Integer rounds;
}
