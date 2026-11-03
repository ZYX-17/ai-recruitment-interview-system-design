package com.recruit.auth.controller;

import com.recruit.auth.dto.AuditLogPageQuery;
import com.recruit.auth.dto.AuditLogView;
import com.recruit.auth.dto.LoginLogPageQuery;
import com.recruit.auth.dto.LoginLogView;
import com.recruit.auth.service.AdminAuditService;
import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志端点（A-15 / P2-5）：操作日志与登录日志分页。
 *
 * <p>两类日志共用 {@code iam_audit_log}，只读；登录日志固定 {@code action = 'LOGIN'}，
 * 成功/失败都返回，失败时操作者可能为空。</p>
 */
@RestController
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    /**
     * 操作日志分页。
     *
     * @param query 分页与过滤条件
     * @return 分页操作日志
     */
    @GetMapping("/api/admin/audit-logs")
    @PreAuthorize("@permissionChecker.has('system:log:oper:view')")
    public Result<PageResult<AuditLogView>> auditLogs(@Valid AuditLogPageQuery query) {
        return Result.ok(adminAuditService.auditPage(query));
    }

    /**
     * 登录日志分页。
     *
     * @param query 分页与过滤条件
     * @return 分页登录日志
     */
    @GetMapping("/api/admin/login-logs")
    @PreAuthorize("@permissionChecker.has('system:log:login:view')")
    public Result<PageResult<LoginLogView>> loginLogs(@Valid LoginLogPageQuery query) {
        return Result.ok(adminAuditService.loginPage(query));
    }
}
