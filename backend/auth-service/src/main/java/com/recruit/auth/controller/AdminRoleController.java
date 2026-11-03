package com.recruit.auth.controller;

import com.recruit.auth.dto.AssignPermissionsForm;
import com.recruit.auth.dto.CreateRoleForm;
import com.recruit.auth.dto.RoleDetailView;
import com.recruit.auth.dto.RolePageQuery;
import com.recruit.auth.dto.RoleView;
import com.recruit.auth.dto.UpdateRoleForm;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.AdminRoleService;
import com.recruit.auth.support.IpUtil;
import com.recruit.auth.support.PrincipalHelper;
import com.recruit.common.result.PageResult;
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

/**
 * 角色管理端点（A-12 / A-13 / A-14 与 X-09 角色详情）。
 *
 * <p>权限码由 {@code @permissionChecker} 现查角色授权，不依赖令牌内权限声明；
 * 所有写操作写审计，分配权限后受影响用户的权限缓存即时失效。</p>
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    /**
     * 角色分页（A-12）。
     *
     * @param query 分页与过滤条件
     * @return 分页角色
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('system:role:view')")
    public Result<PageResult<RoleView>> page(@Valid RolePageQuery query) {
        return Result.ok(adminRoleService.page(query));
    }

    /**
     * 角色详情（X-09）。
     *
     * @param id 角色 ID
     * @return 角色详情（含已分配权限 ID）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:role:view')")
    public Result<RoleDetailView> detail(@PathVariable long id) {
        return Result.ok(adminRoleService.detail(id));
    }

    /**
     * 新增角色（A-12）。
     *
     * @param form      新增入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 新角色
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('system:role:create')")
    public Result<RoleView> create(@Valid @RequestBody CreateRoleForm form,
                                   @AuthenticationPrincipal UserPrincipal principal,
                                   HttpServletRequest request) {
        return Result.ok(adminRoleService.create(form,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 修改角色（A-13）。
     *
     * @param id        角色 ID
     * @param form      修改入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的角色
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:role:update')")
    public Result<RoleView> update(@PathVariable long id,
                                   @Valid @RequestBody UpdateRoleForm form,
                                   @AuthenticationPrincipal UserPrincipal principal,
                                   HttpServletRequest request) {
        return Result.ok(adminRoleService.update(id, form,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 删除角色（A-13）。
     *
     * @param id        角色 ID
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 统一响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:role:delete')")
    public Result<Void> delete(@PathVariable long id,
                               @AuthenticationPrincipal UserPrincipal principal,
                               HttpServletRequest request) {
        adminRoleService.delete(id, PrincipalHelper.operatorId(principal), IpUtil.clientIp(request));
        return Result.ok();
    }

    /**
     * 分配权限/按钮（A-14）。
     *
     * @param id        角色 ID
     * @param form      权限入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的角色详情
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("@permissionChecker.has('system:role:assign-menu')")
    public Result<RoleDetailView> assignPermissions(@PathVariable long id,
                                                    @Valid @RequestBody AssignPermissionsForm form,
                                                    @AuthenticationPrincipal UserPrincipal principal,
                                                    HttpServletRequest request) {
        return Result.ok(adminRoleService.assignPermissions(id, form, false,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 分配菜单（A-14，只允许 MENU 类型）。
     *
     * @param id        角色 ID
     * @param form      权限入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的角色详情
     */
    @PutMapping("/{id}/menus")
    @PreAuthorize("@permissionChecker.has('system:role:assign-menu')")
    public Result<RoleDetailView> assignMenus(@PathVariable long id,
                                              @Valid @RequestBody AssignPermissionsForm form,
                                              @AuthenticationPrincipal UserPrincipal principal,
                                              HttpServletRequest request) {
        return Result.ok(adminRoleService.assignPermissions(id, form, true,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }
}
