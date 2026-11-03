package com.recruit.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 新增流程模板入参（{@code POST /api/interview/process-templates}）。
 */
@Data
public class ProcessTemplateCreateForm {

    @NotBlank
    private String jobId;

    @NotEmpty
    @Valid
    private List<StageDef> stageFlow;

    @NotEmpty
    @Valid
    private List<ScoreDim> scoreDims;
}
