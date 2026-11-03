package com.recruit.gateway.filter;

import com.recruit.common.context.UserContext;
import com.recruit.gateway.security.GatewayUser;
import com.recruit.gateway.support.GatewayAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 可信头清洗与注入（docs/4 §3.1.4 第 4 位，S7）。
 *
 * <p>客户端传入的 {@code X-User-*}、{@code X-Internal-Token} 一律删除，
 * 只有 {@link JwtAuthGlobalFilter} 验签通过后才由本过滤器写入用户头。
 * 匿名白名单请求也会被清洗，防止绕过登录直接伪造身份。</p>
 */
@Slf4j
@Component
public class UserHeaderFilter implements GlobalFilter, Ordered {

    private static final List<String> UNTRUSTED_HEADERS = List.of(
            UserContext.HEADER_USER_ID,
            UserContext.HEADER_USER_ROLE,
            UserContext.HEADER_USER_NAME_B64,
            "X-Internal-Token"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        builder.headers(headers -> UNTRUSTED_HEADERS.forEach(headers::remove));

        GatewayUser user = exchange.getAttribute(GatewayAttributes.USER);
        if (user != null) {
            builder.header(UserContext.HEADER_USER_ID, user.userId());
            if (!user.roles().isEmpty()) {
                builder.header(UserContext.HEADER_USER_ROLE, String.join(",", user.roles()));
            }
        }
        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
