package com.recruit.auth.security;

import com.recruit.auth.config.SecurityProperties;
import com.recruit.auth.support.ApiErrorWriter;
import com.recruit.common.result.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间内部接口令牌校验：{@code /internal/**} 必须携带 {@code X-Internal-Token}。
 *
 * <p>网关已经把公网的 {@code /internal/**} 全部 403；本过滤器防的是“绕过网关直连服务端口”。
 * 未配置令牌时一律拒绝（fail-closed），避免空配置被当成“无需鉴权”。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    /** 内部令牌请求头（网关注入/Feign 透传，客户端伪造头会被网关剥离）。 */
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private static final String INTERNAL_PREFIX = "/internal/";

    private final SecurityProperties properties;
    private final ApiErrorWriter apiErrorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(INTERNAL_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String expected = properties.getInternalToken();
        String provided = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (expected == null || expected.isBlank()) {
            log.warn("未配置 INTERNAL_TOKEN，拒绝内部接口访问: {}", request.getRequestURI());
            apiErrorWriter.write(response, ErrorCode.GW_INTERNAL_FORBIDDEN,
                    ErrorCode.GW_INTERNAL_FORBIDDEN.getDefaultMessage());
            return;
        }
        if (!constantTimeEquals(expected, provided)) {
            log.warn("内部令牌不匹配，拒绝访问: {}", request.getRequestURI());
            apiErrorWriter.write(response, ErrorCode.GW_INTERNAL_FORBIDDEN,
                    ErrorCode.GW_INTERNAL_FORBIDDEN.getDefaultMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
