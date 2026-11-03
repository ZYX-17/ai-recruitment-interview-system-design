package com.recruit.auth.controller;

import com.recruit.auth.dto.MenuNodeView;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.PermissionService;
import com.recruit.auth.support.PrincipalHelper;
import com.recruit.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 动态菜单端点（A-06）。
 *
 * <p>登录即可访问；只返回当前用户可见的 MENU 节点，BUTTON/API 不进入菜单树。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthMenuController {

    private final PermissionService permissionService;

    /**
     * 当前用户菜单树（A-06）。
     *
     * @param principal 当前登录主体
     * @return 菜单树
     */
    @GetMapping("/menus")
    public Result<List<MenuNodeView>> menus(@AuthenticationPrincipal UserPrincipal principal) {
        return Result.ok(permissionService.menus(PrincipalHelper.operatorId(principal)));
    }
}
