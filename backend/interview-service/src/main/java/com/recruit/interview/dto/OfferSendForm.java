package com.recruit.interview.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发送 Offer 入参（{@code POST /api/interview/offers/{id}/send}）。
 */
@Data
public class OfferSendForm {

    private List<String> channel;

    @Size(max = 64)
    private String templateCode;

    private Integer version;
}
