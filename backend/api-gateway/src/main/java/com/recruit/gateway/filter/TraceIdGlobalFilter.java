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
 * traceId 生成/透传（最高优先级）：无 X-Trace-Id 则生成，写入 MDC 并回写响应头。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdUtil.generate();
        } else {
            TraceIdUtil.put(traceId);
        }
        String finalTraceId = traceId;
        ServerHttpRequest mutated = exchange.getRequest().mutate().header(TRACE_HEADER, finalTraceId).build();
        exchange.getResponse().getHeaders().set(TRACE_HEADER, finalTraceId);
        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signal -> TraceIdUtil.clear());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
