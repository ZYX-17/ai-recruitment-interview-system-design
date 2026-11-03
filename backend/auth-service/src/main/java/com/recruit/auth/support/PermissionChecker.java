package com.recruit.auth.support;

import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 方法级权限校验器（配合 {@code @PreAuthorize("@permissionChecker.has('...')")}）。
 *
 * <p>为什么不在 JWT 里塞权限码：docs/4 §7.1.1 要求令牌只带角色，权限以服务端为准，
 * 角色授权变更后下一次请求立即生效。这里按当前用户现查，P2-6 再叠加 {@code auth:perm} 缓存。</p>
 *
 * <p>任何异常都返回 false（fail-closed），避免权限查询失败时放大权限。</p>
 */
@Component("permissionChecker")
@RequiredArgsConstructor
public class PermissionChecker {

    private final PermissionService permissionService;

    /**
     * 当前用户是否拥有指定权限码。
     *
     * @param permissionCode 权限码，如 {@code system:user:view}
     * @return 有权限返回 true
     */
    public boolean has(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        Long userId = currentUserId();
        if (userId == null) {
            return false;
        }
        try {
            return permissionService.permissionCodes(userId).contains(permissionCode);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 当前用户是否拥有任意一个权限码。
     *
     * @param permissionCodes 权限码数组
     * @return 命中任意一个返回 true
     */
    public boolean hasAny(String... permissionCodes) {
        if (permissionCodes == null) {
            return false;
        }
        for (String code : permissionCodes) {
            if (has(code)) {
                return true;
            }
        }
        return false;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }
        try {
            return Long.parseLong(principal.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
