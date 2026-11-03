package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.OfferRespondForm;
import com.recruit.interview.dto.PortalOfferView;
import com.recruit.interview.service.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer 候选人门户（免登录，SECURITY 白名单 {@code /api/interview/portal/**}）。
 *
 * <p>只暴露候选人必要字段；token 校验/过期判断/CAS 都在 Service 内完成。</p>
 */
@RestController
@RequestMapping("/api/interview/portal/offers")
@RequiredArgsConstructor
public class OfferPortalController {

    private final OfferService offerService;

    /**
     * 按 token 查看 Offer。
     *
     * @param token 明文 token
     * @return 门户视图
     */
    @GetMapping("/{token}")
    public Result<PortalOfferView> detail(@PathVariable String token) {
        return Result.ok(offerService.portalDetail(token));
    }

    /**
     * 接受/拒绝 Offer。
     *
     * @param token 明文 token
     * @param form  决策入参
     * @return 处理后的门户视图
     */
    @PostMapping("/{token}/respond")
    public Result<PortalOfferView> respond(@PathVariable String token,
                                           @Valid @RequestBody OfferRespondForm form) {
        return Result.ok(offerService.respond(token, form));
    }
}
