package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.OutboxMessageView;
import com.recruit.interview.dto.OutboxPageQuery;
import com.recruit.interview.service.OutboxAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbox 管理端点（X-03/X-12，网关 {@code /api/admin/outbox/interview/**}）。
 */
@RestController
@RequestMapping("/api/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxAdminService outboxAdminService;

    /**
     * Outbox 消息列表。
     *
     * @param query 过滤与分页
     * @return 分页消息
     */
    @GetMapping("/{service}/messages")
    @PreAuthorize("@permissionChecker.has('system:config:update')")
    public Result<PageResult<OutboxMessageView>> page(@PathVariable String service,
                                                      @Valid OutboxPageQuery query) {
        outboxAdminService.requireService(service);
        return Result.ok(outboxAdminService.page(query));
    }

    /**
     * 重放 DEAD/FAILED 消息。
     *
     * @param id 消息 ID
     * @return 重放后的消息
     */
    @PostMapping("/{service}/{id}/replay")
    @PreAuthorize("@permissionChecker.has('system:config:update')")
    public Result<OutboxMessageView> replay(@PathVariable String service, @PathVariable long id) {
        outboxAdminService.requireService(service);
        return Result.ok(outboxAdminService.replay(id));
    }
}
