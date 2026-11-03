package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.PortalApplicationCreateForm;
import com.recruit.interview.dto.PortalApplicationPageQuery;
import com.recruit.interview.dto.PortalApplicationView;
import com.recruit.interview.service.PortalApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户“我的投递”（I-09）。
 *
 * <p>候选人身份取网关注入的当前用户；服务层再按 {@code person_id} 做归属校验。</p>
 */
@RestController
@RequestMapping("/api/interview/portal/applications")
@RequiredArgsConstructor
public class PortalApplicationController {

    private final PortalApplicationService portalApplicationService;

    /**
     * 我的投递分页。
     *
     * @param query 分页与状态
     * @return 分页投递
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('portal:application:view')")
    public Result<PageResult<PortalApplicationView>> page(@Valid PortalApplicationPageQuery query) {
        return Result.ok(portalApplicationService.page(query));
    }

    /**
     * 投递岗位。
     *
     * @param form 投递入参
     * @param key  Idempotency-Key，可选
     * @return 新投递
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('portal:application:create')")
    public Result<PortalApplicationView> create(
            @Valid @RequestBody PortalApplicationCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(portalApplicationService.create(form, key));
    }

    /**
     * 撤回投递。
     *
     * @param id 投递 ID
     * @return 撤回后的投递
     */
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("@permissionChecker.has('portal:application:withdraw')")
    public Result<PortalApplicationView> withdraw(@PathVariable long id) {
        return Result.ok(portalApplicationService.withdraw(id));
    }
}
