package com.recruit.auth.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全过滤器链里的错误出口。
 *
 * <p>Spring Security 的 401/403 默认返回空 body 或 HTML，前端无法按统一 {@code Result} 解析。
 * 这里保证安全层错误与业务接口同构：{@code {code,message,data,traceId,timestamp}}。</p>
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    /**
     * 写出统一错误响应。
     *
     * @param response HTTP 响应
     * @param errorCode 错误码（决定 HTTP 状态与业务码）
     * @param message 展示文案
     * @throws IOException 写响应失败
     */
    public void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Result.fail(errorCode, message));
    }
}
