package com.recruit.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 修改流程模板入参（{@code PUT /api/interview/process-templates/{id}}）。
 *
 * <p>修改不是原地更新：以 {@code version} 校验当前版本后生成 {@code templateVer + 1} 的新版本，
 * 旧版本保留只读，保证在途投递与历史评价可追溯。</p>
 */
@Data
public class ProcessTemplateUpdateForm {

    @NotEmpty
    @Valid
    private List<StageDef> stageFlow;

    @NotEmpty
    @Valid
    private List<ScoreDim> scoreDims;

    @NotNull
    private Integer version;
}
