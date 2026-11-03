package com.recruit.auth.controller;

import com.recruit.auth.dto.CreatePermissionForm;
import com.recruit.auth.dto.PermissionNodeView;
import com.recruit.auth.dto.UpdatePermissionForm;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.AdminPermissionService;
import com.recruit.auth.support.IpUtil;
import com.recruit.auth.support.PrincipalHelper;
import com.recruit.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限管理端点（A-15）：权限树与权限 CRUD。
 *
 * <p>权限定义变更后清理全部权限缓存，角色勾选树与前端菜单下一次请求即拿到新结构。</p>
 */
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
public class AdminPermissionController {

    private final AdminPermissionService adminPermissionService;

    /**
     * 权限树（A-15）。
     *
     * @return 权限树（含停用节点）
     */
    @GetMapping("/tree")
    @PreAuthorize("@permissionChecker.has('system:menu:view')")
    public Result<List<PermissionNodeView>> tree() {
        return Result.ok(adminPermissionService.tree());
    }

    /**
     * 新增权限（A-15）。
     *
     * @param form      新增入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 新权限节点
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('system:menu:create')")
    public Result<PermissionNodeView> create(@Valid @RequestBody CreatePermissionForm form,
                                             @AuthenticationPrincipal UserPrincipal principal,
                                             HttpServletRequest request) {
        return Result.ok(adminPermissionService.create(form,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 修改权限（A-15）。
     *
     * @param id        权限 ID
     * @param form      修改入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的权限节点
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:menu:update')")
    public Result<PermissionNodeView> update(@PathVariable long id,
                                             @Valid @RequestBody UpdatePermissionForm form,
                                             @AuthenticationPrincipal UserPrincipal principal,
                                             HttpServletRequest request) {
        return Result.ok(adminPermissionService.update(id, form,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 删除权限（A-15）。
     *
     * @param id        权限 ID
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 统一响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:menu:delete')")
    public Result<Void> delete(@PathVariable long id,
                               @AuthenticationPrincipal UserPrincipal principal,
                               HttpServletRequest request) {
        adminPermissionService.delete(id, PrincipalHelper.operatorId(principal), IpUtil.clientIp(request));
        return Result.ok();
    }
}
