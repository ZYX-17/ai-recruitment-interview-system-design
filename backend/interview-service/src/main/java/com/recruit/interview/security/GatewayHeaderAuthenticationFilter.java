package com.recruit.interview.security;

import com.recruit.common.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 信任网关注入的 {@code X-User-*}，还原 SecurityContext。
 *
 * <p>业务服务不自行验 JWT：验签/黑名单在网关完成（docs/4 §2.2）。服务端口不能暴露公网，
 * 否则客户端可直接伪造 {@code X-User-Uid}，所以网关必须剥离客户端同名头（P3-2）。</p>
 */
@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    static final String ROLE_PREFIX = "ROLE_";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(UserContext.HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            List<String> roles = parseRoles(request.getHeader(UserContext.HEADER_USER_ROLE));
            CurrentUser principal = new CurrentUser(userId, roles);
            List<GrantedAuthority> authorities = roles.stream()
                    .map(role -> role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role)
                    .map(SimpleGrantedAuthority::new)
                    .map(GrantedAuthority.class::cast)
                    .toList();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static List<String> parseRoles(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .distinct()
                .toList();
    }
}
