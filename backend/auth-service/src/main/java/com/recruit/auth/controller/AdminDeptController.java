package com.recruit.auth.controller;

import com.recruit.auth.dto.CreateDeptForm;
import com.recruit.auth.dto.DeptNodeView;
import com.recruit.auth.dto.UpdateDeptForm;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.AdminDeptService;
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
 * 部门管理端点（P2-5 扩展）：部门树与 CRUD。
 *
 * <p>部门是数据范围（DEPARTMENT）的基础，新增/移动会维护 {@code ancestors}，
 * 变更后下一次用户列表查询即按新树过滤，无需额外缓存失效。</p>
 */
@RestController
@RequestMapping("/api/admin/depts")
@RequiredArgsConstructor
public class AdminDeptController {

    private final AdminDeptService adminDeptService;

    /**
     * 部门树（含停用节点）。
     *
     * @return 部门树
     */
    @GetMapping("/tree")
    @PreAuthorize("@permissionChecker.has('system:dept:view')")
    public Result<List<DeptNodeView>> tree() {
        return Result.ok(adminDeptService.tree());
    }

    /**
     * 新增部门。
     *
     * @param form      新增入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 新部门节点
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('system:dept:create')")
    public Result<DeptNodeView> create(@Valid @RequestBody CreateDeptForm form,
                                       @AuthenticationPrincipal UserPrincipal principal,
                                       HttpServletRequest request) {
        return Result.ok(adminDeptService.create(form,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 修改部门。
     *
     * @param id        部门 ID
     * @param form      修改入参
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 更新后的部门节点
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:dept:update')")
    public Result<DeptNodeView> update(@PathVariable long id,
                                       @Valid @RequestBody UpdateDeptForm form,
                                       @AuthenticationPrincipal UserPrincipal principal,
                                       HttpServletRequest request) {
        return Result.ok(adminDeptService.update(id, form,
                PrincipalHelper.operatorId(principal), IpUtil.clientIp(request)));
    }

    /**
     * 删除部门。
     *
     * @param id        部门 ID
     * @param principal 当前登录主体
     * @param request   HTTP 请求（取 IP 写审计）
     * @return 统一响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:dept:delete')")
    public Result<Void> delete(@PathVariable long id,
                               @AuthenticationPrincipal UserPrincipal principal,
                               HttpServletRequest request) {
        adminDeptService.delete(id, PrincipalHelper.operatorId(principal), IpUtil.clientIp(request));
        return Result.ok();
    }
}
