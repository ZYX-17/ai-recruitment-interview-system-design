package com.recruit.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.gateway.support.GatewayErrorWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link InternalApiBlockFilter}：{@code /internal/**} 优先于 JWT 直接 403。
 */
class InternalApiBlockFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InternalApiBlockFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiBlockFilter(new GatewayErrorWriter(objectMapper));
    }

    @Test
    void internalPathIsBlockedWithGw9005() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/notify/send").build());

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertEquals("GW9005", objectMapper.readTree(body).path("code").asText());
    }

    @Test
    void nestedInternalPathIsBlocked() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/proxy/internal/x").build());

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void normalPathPassesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capture(downstream)).block();

        assertNotNull(downstream.get());
    }

    private static org.springframework.cloud.gateway.filter.GatewayFilterChain capture(
            AtomicReference<ServerWebExchange> ref) {
        return exchange -> {
            ref.set(exchange);
            return Mono.empty();
        };
    }
}
