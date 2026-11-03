package com.recruit.auth.support;

import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.security.TokenType;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionChecker} 的鉴权语义测试。
 */
@ExtendWith(MockitoExtension.class)
class PermissionCheckerTest {

    @Mock
    private PermissionService permissionService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantsWhenCurrentUserHasPermissionCode() {
        authenticate();
        when(permissionService.permissionCodes(10001L))
                .thenReturn(List.of("system:user:view", "system:user:create"));

        PermissionChecker checker = new PermissionChecker(permissionService);

        assertTrue(checker.has("system:user:view"));
        assertFalse(checker.has("system:user:delete"));
        assertTrue(checker.hasAny("system:user:delete", "system:user:create"));
    }

    @Test
    void deniesAnonymousRequests() {
        PermissionChecker checker = new PermissionChecker(permissionService);

        assertFalse(checker.has("system:user:view"));
        assertFalse(checker.hasAny((String[]) null));
    }

    @Test
    void failsClosedWhenPermissionQueryThrows() {
        authenticate();
        when(permissionService.permissionCodes(10001L)).thenThrow(new IllegalStateException("db down"));

        PermissionChecker checker = new PermissionChecker(permissionService);

        assertFalse(checker.has("system:user:view"));
    }

    private static void authenticate() {
        Instant now = Instant.now();
        UserPrincipal principal = UserPrincipal.from(new JwtClaims("10001", "admin", List.of("SUPER_ADMIN"), 0L,
                "jti-1", TokenType.ACCESS, null, now, now.plusSeconds(600)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
