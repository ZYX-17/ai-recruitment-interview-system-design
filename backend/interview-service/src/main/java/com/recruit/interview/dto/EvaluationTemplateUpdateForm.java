package com.recruit.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 修改评价模板入参（{@code PUT /api/interview/evaluation-templates/{id}}）。
 *
 * <p>生成 {@code templateVer+1} 的新版本，旧版本只读；{@code stageFlow} 省略时沿用当前版本。</p>
 */
@Data
public class EvaluationTemplateUpdateForm {

    @Valid
    private List<StageDef> stageFlow;

    @NotEmpty
    @Valid
    private List<ScoreDim> scoreDims;

    @NotNull
    private Integer version;
}
