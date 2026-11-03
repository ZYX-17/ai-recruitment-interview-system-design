package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.ApplicationActionForm;
import com.recruit.interview.dto.ApplicationCreateForm;
import com.recruit.interview.dto.ApplicationPageQuery;
import com.recruit.interview.dto.ApplicationTimelineView;
import com.recruit.interview.dto.ApplicationView;
import com.recruit.interview.service.ApplicationService;
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

import java.util.List;

/**
 * 投递端点（OpenAPI §7）。
 *
 * <p>动作接口先做“具备任一投递写权限”的粗过滤，Service 再按 {@code action} 精确校验权限码，
 * 防止只有“推进”权限的人调用“淘汰/转岗/撤回”。</p>
 */
@RestController
@RequestMapping("/api/interview/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private static final String ACTION_PERMISSIONS =
            "'interview:application:update','interview:application:reject',"
                    + "'interview:application:transfer','interview:application:withdraw'";

    private final ApplicationService applicationService;

    /**
     * 投递分页。
     *
     * @param query 分页与过滤条件
     * @return 分页投递
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('interview:application:view')")
    public Result<PageResult<ApplicationView>> page(@Valid ApplicationPageQuery query) {
        return Result.ok(applicationService.page(query));
    }

    /**
     * 投递详情。
     *
     * @param id 投递 ID
     * @return 投递详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:application:view')")
    public Result<ApplicationView> detail(@PathVariable long id) {
        return Result.ok(applicationService.detail(id));
    }

    /**
     * 投递时间线。
     *
     * @param id 投递 ID
     * @return 时间线
     */
    @GetMapping("/{id}/timeline")
    @PreAuthorize("@permissionChecker.has('interview:application:view')")
    public Result<List<ApplicationTimelineView>> timeline(@PathVariable long id) {
        return Result.ok(applicationService.timeline(id));
    }

    /**
     * 新增投递。
     *
     * @param form 新增入参
     * @param key  Idempotency-Key（可选）
     * @return 新投递
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('interview:application:create')")
    public Result<ApplicationView> create(
            @Valid @RequestBody ApplicationCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(applicationService.create(form, key));
    }

    /**
     * 投递动作。
     *
     * @param id   投递 ID
     * @param form 动作入参
     * @return 动作后的投递
     */
    @PostMapping("/{id}/actions")
    @PreAuthorize("@permissionChecker.hasAny(" + ACTION_PERMISSIONS + ")")
    public Result<ApplicationView> action(@PathVariable long id,
                                          @Valid @RequestBody ApplicationActionForm form) {
        return Result.ok(applicationService.action(id, form));
    }
}
