package com.recruit.auth.security;

import com.recruit.auth.support.ApiErrorWriter;
import com.recruit.common.result.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证统一出口：HTTP 401 + {@code GW9001}。
 *
 * <p>过期与其它非法令牌用同一个业务码，只改展示文案：前端按 401 走重新登录，
 * 无需为“过期”单独分支；日志侧仍可从过滤器保留的异常区分原因。</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter apiErrorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object cause = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String message = cause instanceof ExpiredJwtException
                ? "登录已过期，请重新登录"
                : ErrorCode.GW_UNAUTHENTICATED.getDefaultMessage();
        apiErrorWriter.write(response, ErrorCode.GW_UNAUTHENTICATED, message);
    }
}
