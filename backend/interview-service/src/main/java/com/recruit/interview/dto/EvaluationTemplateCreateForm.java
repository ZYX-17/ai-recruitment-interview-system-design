package com.recruit.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 新增评价模板入参（{@code POST /api/interview/evaluation-templates}）。
 *
 * <p>评价模板与流程模板同源（{@code job_stage_template} + {@code evaluation_dimension}，docs/4 §7.6）：
 * 只维护评分维度；{@code stageFlow} 省略时沿用岗位当前生效流程，避免出现“有维度、无阶段”的模板。</p>
 */
@Data
public class EvaluationTemplateCreateForm {

    @NotBlank
    private String jobId;

    @Valid
    private List<StageDef> stageFlow;

    @NotEmpty
    @Valid
    private List<ScoreDim> scoreDims;
}
