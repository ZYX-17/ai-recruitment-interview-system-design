package com.recruit.auth.security;

import com.recruit.auth.support.ApiErrorWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从 {@code Authorization: Bearer <access>} 还原登录态。
 *
 * <p>只做三件事：取头、验签、放主体。失败时不在这里直接响应，
 * 而是清空上下文并把异常挂到 request 上，交给
 * {@link RestAuthenticationEntryPoint} 统一输出 401——这样白名单接口
 * 即使带了过期令牌也能正常访问，受保护接口仍然 fail-closed。</p>
 *
 * <p>令牌版本 {@code tv} 与黑名单校验不在本过滤器：网关 P3-1 负责对业务流量比对
 * Redis {@code auth:tv:{userId}}，P1-5 负责登出/改密后的失效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败原因，供入口点细分文案。 */
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final ApiErrorWriter apiErrorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            JwtClaims claims = tokenProvider.parseAccessToken(token);
            UserPrincipal principal = UserPrincipal.from(claims);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, e);
            log.debug("Access Token 校验失败：{}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
