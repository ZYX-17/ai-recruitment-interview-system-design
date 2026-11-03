package com.recruit.gateway.filter;

import com.recruit.common.trace.TraceIdUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * traceId 生成与透传（最高优先级）。
 *
 * <p><b>不信任客户端传入的 {@code X-Trace-Id}</b>：客户端可以伪造它污染日志或绕过
 * 链路统计，所以每次入口请求都重新生成，再写入 MDC、请求头和响应头。</p>
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceIdUtil.generate();
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TRACE_HEADER, traceId))
                .build();
        exchange.getResponse().getHeaders().set(TRACE_HEADER, traceId);
        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signal -> TraceIdUtil.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
