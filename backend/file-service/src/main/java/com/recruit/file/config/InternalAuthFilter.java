package com.recruit.file.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口鉴权：只保护 {@code /internal/**}，要求 {@code X-Internal-Token}。
 *
 * <p>网关负责把 {@code /internal/**} 挡在公网之外；服务间调用（resume-service → file-service）
 * 还必须带内部令牌，形成第二道防线。令牌用常量时间比较，避免按字节探测。</p>
 *
 * <p>过滤器顺序放在 UserContextFilter（HIGHEST_PRECEDENCE + 20）之前，
 * 内部请求不依赖网关注入的用户头。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class InternalAuthFilter extends OncePerRequestFilter {

    /** 内部调用令牌头。 */
    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final ObjectMapper objectMapper;

    @Value("${recruit.internal.token:dev-internal-token}")
    private String expectedToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (!matches(provided)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Result.fail(ErrorCode.GW_INTERNAL_FORBIDDEN));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String provided) {
        if (provided == null || expectedToken == null || expectedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }
}
