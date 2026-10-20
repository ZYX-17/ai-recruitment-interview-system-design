package com.recruit.common.web;

import com.recruit.common.context.UserContext;
import com.recruit.common.trace.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 从网关注入的可信头恢复当前用户，并在请求结束时清理 ThreadLocal。
 *
 * <p>只读取 X-User-Uid / X-User-Role / X-User-Name-B64 / X-Trace-Id；
 * 中文姓名不直接放 Header，使用 UTF-8 Base64。</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = trimToNull(request.getHeader(UserContext.HEADER_TRACE_ID));
        if (traceId != null) {
            TraceIdUtil.put(traceId);
        }

        String userId = trimToNull(request.getHeader(UserContext.HEADER_USER_ID));
        if (userId != null) {
            UserContext.set(
                    userId,
                    parseRoles(request.getHeader(UserContext.HEADER_USER_ROLE)),
                    decodeDisplayName(request.getHeader(UserContext.HEADER_USER_NAME_B64)),
                    traceId
            );
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            if (traceId != null) {
                TraceIdUtil.clear();
            }
        }
    }

    private Set<String> parseRoles(String rawRoles) {
        String value = trimToNull(rawRoles);
        if (value == null) {
            return Collections.emptySet();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : value.split(",")) {
            String trimmed = role.trim();
            if (!trimmed.isEmpty()) {
                roles.add(trimmed);
            }
        }
        return roles;
    }

    private String decodeDisplayName(String rawName) {
        String value = trimToNull(rawName);
        if (value == null) {
            return null;
        }
        try {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException standardFailure) {
                bytes = Base64.getUrlDecoder().decode(value);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.debug("X-User-Name-B64 解析失败，忽略该头");
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
