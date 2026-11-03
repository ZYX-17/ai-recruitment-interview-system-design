package com.recruit.auth.controller;

import com.recruit.auth.dto.AdminUserView;
import com.recruit.auth.dto.AssignRolesForm;
import com.recruit.auth.dto.CreateUserForm;
import com.recruit.auth.dto.CreatedUserResult;
import com.recruit.auth.dto.ResetPasswordResult;
import com.recruit.auth.dto.UpdateUserForm;
import com.recruit.auth.dto.UserPageQuery;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.AdminUserService;
import com.recruit.auth.support.IpUtil;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
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
 * 用户管理端点（A-07~A-10、X-09、X-10）。
 *
 * <p>权限码由 {@code @permissionChecker} 现查角色授权，不依赖令牌内权限声明；
 * 所有写操作写审计，重置密码/禁用会撤销目标用户全部会话。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 用户分页（A-07）。
     *
     * @param query     分页与过滤条件
     * @param principal 当前登录主体
     * @return 分页用户
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('system:user:view')")
    public Result<PageResult<AdminUserView>> page(@Valid UserPageQuery query,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return Result.ok(adminUserService.page(query, operatorId(principal)));
    }

    /**
     * 用户详情（X-09）。
     *
     * @param id        用户 ID
     * @param principal 当前登录主体
     * @return 用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:user:view')")
    public Result<AdminUserView> detail(@PathVariable long id,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return Result.ok(adminUserService.detail(id, operatorId(principal)));
    }

    /**
     * 新增用户（A-08）。
     *
     * @param form      新增入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 新用户 ID 与初始密码
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('system:user:create')")
    public Result<CreatedUserResult> create(@Valid @RequestBody CreateUserForm form,
                                            @AuthenticationPrincipal UserPrincipal principal,
                                            HttpServletRequest request) {
        return Result.ok(adminUserService.create(form, operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 更新用户（A-09）。
     *
     * @param id        用户 ID
     * @param form      更新入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:user:update')")
    public Result<AdminUserView> update(@PathVariable long id,
                                        @Valid @RequestBody UpdateUserForm form,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        return Result.ok(adminUserService.update(id, form, operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 删除用户（A-10，软删）。
     *
     * @param id        用户 ID
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 统一响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:user:delete')")
    public Result<Void> delete(@PathVariable long id,
                               @AuthenticationPrincipal UserPrincipal principal,
                               HttpServletRequest request) {
        adminUserService.delete(id, operatorId(principal), IpUtil.clientIp(request));
        return Result.ok();
    }

    /**
     * 重置密码（X-10）。
     *
     * @param id        用户 ID
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 一次性临时密码
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("@permissionChecker.has('system:user:reset-password')")
    public Result<ResetPasswordResult> resetPassword(@PathVariable long id,
                                                     @AuthenticationPrincipal UserPrincipal principal,
                                                     HttpServletRequest request) {
        return Result.ok(adminUserService.resetPassword(id, operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 分配角色（A-11，全量替换）。
     *
     * @param id        用户 ID
     * @param form      角色入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的用户
     */
    @PutMapping("/{id}/roles")
    @PreAuthorize("@permissionChecker.has('system:user:assign-role')")
    public Result<AdminUserView> assignRoles(@PathVariable long id,
                                             @Valid @RequestBody AssignRolesForm form,
                                             @AuthenticationPrincipal UserPrincipal principal,
                                             HttpServletRequest request) {
        return Result.ok(adminUserService.assignRoles(id, form, operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 强制下线（X-10，幂等）。
     *
     * @param id        用户 ID
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 统一响应
     */
    @PostMapping("/{id}/kick")
    @PreAuthorize("@permissionChecker.has('system:user:kick')")
    public Result<Void> kick(@PathVariable long id,
                             @AuthenticationPrincipal UserPrincipal principal,
                             HttpServletRequest request) {
        adminUserService.kick(id, operatorId(principal), IpUtil.clientIp(request));
        return Result.ok();
    }

    private long operatorId(UserPrincipal principal) {
        if (principal == null) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED);
        }
        try {
            return Long.parseLong(principal.getUserId());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED);
        }
    }
}
