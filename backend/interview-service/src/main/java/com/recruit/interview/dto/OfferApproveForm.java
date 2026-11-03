package com.recruit.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Offer 审批入参（{@code POST /api/interview/offers/{id}/approve}）。
 *
 * <p>简化审批：{@code approved=true} 审批即发送；{@code false} 驳回回 DRAFT。</p>
 */
@Data
public class OfferApproveForm {

    @NotNull
    private Boolean approved;

    @Size(max = 255)
    private String comment;

    private Integer version;
}
