package com.recruit.auth.security;

import com.recruit.auth.support.ApiErrorWriter;
import com.recruit.common.result.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已认证但无权访问：HTTP 403。
 *
 * <p>{@code /internal/**} 是服务间接口，外部流量命中时用 {@code GW9005} 与普通
 * 越权 {@code AUTH1101} 区分，便于网关日志和告警判断是不是配置漏了路由。</p>
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final ApiErrorWriter apiErrorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith(INTERNAL_PATH_PREFIX)) {
            apiErrorWriter.write(response, ErrorCode.GW_INTERNAL_FORBIDDEN,
                    ErrorCode.GW_INTERNAL_FORBIDDEN.getDefaultMessage());
            return;
        }
        apiErrorWriter.write(response, ErrorCode.AUTH_FORBIDDEN, ErrorCode.AUTH_FORBIDDEN.getDefaultMessage());
    }
}
