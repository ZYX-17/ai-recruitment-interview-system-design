package com.recruit.interview.security;

import com.recruit.common.result.ErrorCode;
import com.recruit.interview.config.InterviewSecurityProperties;
import com.recruit.interview.support.SecurityErrorWriter;
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
 * 服务间内部接口令牌校验（{@code /internal/**}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private static final String INTERNAL_PREFIX = "/internal/";

    private final InterviewSecurityProperties properties;
    private final SecurityErrorWriter securityErrorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(INTERNAL_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String expected = properties.getInternalToken();
        String provided = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (expected == null || expected.isBlank() || !matches(expected, provided)) {
            log.warn("内部令牌校验失败，拒绝访问: {}", request.getRequestURI());
            securityErrorWriter.write(response, ErrorCode.GW_INTERNAL_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean matches(String expected, String provided) {
        return provided != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
