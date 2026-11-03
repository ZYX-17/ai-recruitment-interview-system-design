package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 投递动作入参（{@code POST /api/interview/applications/{id}/actions}）。
 *
 * <p>{@code comment} 同时作为 {@code REJECT} 的淘汰原因；{@code targetJobId} 仅 {@code TRANSFER} 使用。</p>
 */
@Data
public class ApplicationActionForm {

    /** ADVANCE / PASS / REJECT / HOLD / TRANSFER / WITHDRAW / HIRE。 */
    @NotBlank
    @Size(max = 16)
    private String action;

    @Size(max = 512)
    private String comment;

    private String targetStage;

    private String targetOwnerId;

    private String targetJobId;

    @NotNull
    private Integer version;
}
