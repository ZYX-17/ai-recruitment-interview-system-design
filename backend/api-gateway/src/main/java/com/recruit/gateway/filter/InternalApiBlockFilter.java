package com.recruit.gateway.filter;

import com.recruit.common.result.ErrorCode;
import com.recruit.gateway.support.GatewayErrorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 内部接口屏蔽：{@code /internal/**} 一律 403（错误码 GW9005），仅供服务间一跳调用。
 *
 * <p>优先级高于 JWT 校验：即使请求带合法令牌也不放行，避免内部接口被公网直接调用。</p>
 */
@Component
@RequiredArgsConstructor
public class InternalApiBlockFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_PREFIX = "/internal/";

    private final GatewayErrorWriter errorWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith(INTERNAL_PREFIX) || path.contains(INTERNAL_PREFIX)) {
            return errorWriter.write(exchange, ErrorCode.GW_INTERNAL_FORBIDDEN,
                    ErrorCode.GW_INTERNAL_FORBIDDEN.getDefaultMessage());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
