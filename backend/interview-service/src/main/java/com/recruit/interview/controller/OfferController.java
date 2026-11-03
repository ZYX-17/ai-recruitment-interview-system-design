package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.OfferApproveForm;
import com.recruit.interview.dto.OfferCreateForm;
import com.recruit.interview.dto.OfferPageQuery;
import com.recruit.interview.dto.OfferSendForm;
import com.recruit.interview.dto.OfferView;
import com.recruit.interview.service.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer 管理端端点（OpenAPI §11）。
 */
@RestController
@RequestMapping("/api/interview/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    /**
     * Offer 分页。
     *
     * @param query 分页与过滤条件
     * @return 分页 Offer
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('interview:offer:view')")
    public Result<PageResult<OfferView>> page(@Valid OfferPageQuery query) {
        return Result.ok(offerService.page(query));
    }

    /**
     * 创建 Offer。
     *
     * @param form 创建入参
     * @param key  Idempotency-Key（可选）
     * @return 新 Offer
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('interview:offer:create')")
    public Result<OfferView> create(
            @Valid @RequestBody OfferCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(offerService.create(form, key));
    }

    /**
     * Offer 详情。
     *
     * @param id Offer ID
     * @return 详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:offer:view')")
    public Result<OfferView> detail(@PathVariable long id) {
        return Result.ok(offerService.detail(id));
    }

    /**
     * 发送 Offer。
     *
     * @param id   Offer ID
     * @param form 发送入参
     * @param key  Idempotency-Key（可选）
     * @return 发送后的 Offer
     */
    @PostMapping("/{id}/send")
    @PreAuthorize("@permissionChecker.has('interview:offer:send')")
    public Result<OfferView> send(@PathVariable long id,
                                  @Valid @RequestBody OfferSendForm form,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(offerService.send(id, form, key));
    }

    /**
     * Offer 审批（通过即发送；驳回回 DRAFT）。
     *
     * @param id   Offer ID
     * @param form 审批入参
     * @return 审批后的 Offer
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("@permissionChecker.has('interview:offer:approve')")
    public Result<OfferView> approve(@PathVariable long id,
                                     @Valid @RequestBody OfferApproveForm form) {
        return Result.ok(offerService.approve(id, form));
    }

    /**
     * 撤回未确认 Offer。
     *
     * @param id     Offer ID
     * @param reason 撤回原因（可选）
     * @return 撤回后的 Offer
     */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@permissionChecker.has('interview:offer:withdraw')")
    public Result<OfferView> withdraw(@PathVariable long id,
                                      @RequestParam(required = false) String reason) {
        return Result.ok(offerService.withdraw(id, reason));
    }
}
