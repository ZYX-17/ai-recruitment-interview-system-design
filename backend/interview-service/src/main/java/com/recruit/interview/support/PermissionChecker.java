package com.recruit.interview.support;

import com.recruit.interview.security.CurrentUser;
import com.recruit.interview.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 方法级权限校验器（配合 {@code @PreAuthorize("@permissionChecker.has('...')")}）。
 *
 * <p>fail-closed：未登录、权限查询异常、鉴权服务不可用都返回 false。</p>
 */
@Component("permissionChecker")
@RequiredArgsConstructor
public class PermissionChecker {

    private final PermissionCacheService permissionCacheService;

    /**
     * 当前用户是否拥有指定权限码。
     *
     * @param permissionCode 权限码
     * @return 有权限返回 true
     */
    public boolean has(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        CurrentUser currentUser = currentUser();
        if (currentUser == null) {
            return false;
        }
        try {
            return permissionCacheService.permissionCodes(Long.parseLong(currentUser.userId()))
                    .contains(permissionCode);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 是否命中任意一个权限码。
     *
     * @param codes 权限码数组
     * @return 命中任意一个返回 true
     */
    public boolean hasAny(String... codes) {
        if (codes == null) {
            return false;
        }
        for (String code : codes) {
            if (has(code)) {
                return true;
            }
        }
        return false;
    }

    private static CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return null;
        }
        return currentUser;
    }
}
