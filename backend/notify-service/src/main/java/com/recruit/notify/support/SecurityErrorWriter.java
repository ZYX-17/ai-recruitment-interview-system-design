package com.recruit.notify.support;

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
 * 安全链 401/403 的统一 JSON 出口。
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    /**
     * 写出错误响应。
     *
     * @param response  HTTP 响应
     * @param errorCode 错误码
     * @throws IOException 写响应失败
     */
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Result.fail(errorCode));
    }
}
