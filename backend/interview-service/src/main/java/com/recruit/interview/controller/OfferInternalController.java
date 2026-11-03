package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.ConfirmLinkView;
import com.recruit.interview.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer 内部接口（notify 专用，网关对公网 403，需 {@code X-Internal-Token}）。
 */
@RestController
@RequestMapping("/internal/offers")
@RequiredArgsConstructor
public class OfferInternalController {

    private final OfferService offerService;

    /**
     * 现取一次性确认链接；不落日志、不进 MQ。
     *
     * @param id Offer ID
     * @return 链接与缓存过期时间
     */
    @PostMapping("/{id}/confirm-link")
    public Result<ConfirmLinkView> confirmLink(@PathVariable long id) {
        return Result.ok(offerService.confirmLink(id));
    }
}
