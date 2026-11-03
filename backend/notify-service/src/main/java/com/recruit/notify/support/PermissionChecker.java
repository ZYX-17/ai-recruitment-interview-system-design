package com.recruit.notify.support;

import com.recruit.common.result.Result;
import com.recruit.notify.client.AuthPermissionClient;
import com.recruit.notify.dto.AuthzSnapshot;
import com.recruit.notify.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 方法级权限校验器（配合 {@code @PreAuthorize("@permissionChecker.has('...')")}）。
 *
 * <p>notify 不引入 Redis，直接回源 auth-service；任何异常都返回 false（fail-closed）。</p>
 */
@Slf4j
@Component("permissionChecker")
@RequiredArgsConstructor
public class PermissionChecker {

    private final AuthPermissionClient authPermissionClient;

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
            Result<AuthzSnapshot> result = authPermissionClient.authz(Long.parseLong(currentUser.userId()));
            return result != null && result.isSuccess() && result.getData() != null
                    && result.getData().permissions().contains(permissionCode);
        } catch (RuntimeException e) {
            log.warn("权限查询失败，按无权处理: userId={}, permission={}",
                    currentUser.userId(), permissionCode, e);
            return false;
        }
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
