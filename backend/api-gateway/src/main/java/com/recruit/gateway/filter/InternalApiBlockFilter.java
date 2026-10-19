package com.recruit.gateway.filter;

import com.recruit.common.result.ErrorCode;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 内部接口屏蔽：/internal/** 一律 403（错误码 GW9005），仅供服务间一跳 Feign 调用。
 */
@Component
public class InternalApiBlockFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_PREFIX = "/internal/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith(INTERNAL_PREFIX) || path.contains(INTERNAL_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"code\":\"" + ErrorCode.GW_INTERNAL_FORBIDDEN.getCode()
                    + "\",\"message\":\"" + ErrorCode.GW_INTERNAL_FORBIDDEN.getDefaultMessage() + "\"}";
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
