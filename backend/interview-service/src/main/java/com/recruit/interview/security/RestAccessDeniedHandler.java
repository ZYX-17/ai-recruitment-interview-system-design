package com.recruit.interview.security;

import com.recruit.common.result.ErrorCode;
import com.recruit.interview.support.SecurityErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 已认证但无权：{@code /internal/**} 用 GW9005，其余 403 {@code AUTH1101}。
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final String INTERNAL_PREFIX = "/internal/";

    private final SecurityErrorWriter securityErrorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode code = request.getRequestURI().startsWith(INTERNAL_PREFIX)
                ? ErrorCode.GW_INTERNAL_FORBIDDEN
                : ErrorCode.AUTH_FORBIDDEN;
        securityErrorWriter.write(response, code);
    }
}
