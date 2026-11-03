package com.recruit.gateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 网关统一错误响应：与业务接口同为 {@link Result} 结构。
 *
 * <p>WebFlux 无法复用 MVC 的 {@code @RestControllerAdvice}，所以过滤器里直接写响应。
 * 业务码/HTTP 状态统一从 {@link ErrorCode} 读取，避免 401/403/429/503 各写一套。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayErrorWriter {

    private final ObjectMapper objectMapper;

    /**
     * 写出错误响应。
     *
     * @param exchange 当前请求
     * @param errorCode 错误码
     * @param message 展示文案
     * @return 响应完成信号
     */
    public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode, String message) {
        return write(exchange, errorCode, message, headers -> {
        });
    }

    /**
     * 写出错误响应，并允许补充响应头（如 {@code Retry-After}）。
     *
     * @param exchange  当前请求
     * @param errorCode 错误码
     * @param message   展示文案
     * @param headers   额外响应头
     * @return 响应完成信号
     */
    public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode, String message,
                            Consumer<HttpHeaders> headers) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.accept(response.getHeaders());
        byte[] body = serialize(errorCode, message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private byte[] serialize(ErrorCode errorCode, String message) {
        try {
            return objectMapper.writeValueAsString(Result.fail(errorCode, message))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            log.error("网关错误响应序列化失败: {}", errorCode.getCode(), e);
            return ("{\"code\":\"" + errorCode.getCode() + "\",\"message\":\""
                    + errorCode.getDefaultMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
