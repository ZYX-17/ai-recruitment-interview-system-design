package com.recruit.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraceIdGlobalFilter}：客户端伪造的 traceId 不得透传。
 */
class TraceIdGlobalFilterTest {

    private final TraceIdGlobalFilter filter = new TraceIdGlobalFilter();

    @Test
    void forgedTraceIdIsReplaced() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs")
                        .header("X-Trace-Id", "forged-trace")
                        .build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        }).block();

        String forwarded = downstream.get().getRequest().getHeaders().getFirst("X-Trace-Id");
        String responseHeader = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
        assertNotEquals("forged-trace", forwarded);
        assertTrue(forwarded.matches("[0-9a-f]{32}"));
        assertEquals(forwarded, responseHeader);
    }
}
