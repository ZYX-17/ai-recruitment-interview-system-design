package com.recruit.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 门户确认/拒绝 Offer 入参（{@code POST /api/interview/portal/offers/{token}/respond}）。
 */
@Data
public class OfferRespondForm {

    /** ACCEPT / DECLINE。 */
    @NotBlank
    @Size(max = 16)
    private String decision;

    @Size(max = 500)
    private String comment;

    /** 候选人姓名，用于二次确认；可不填。 */
    @Size(max = 64)
    private String name;
}
