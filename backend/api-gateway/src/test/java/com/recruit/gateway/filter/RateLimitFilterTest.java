package com.recruit.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.constant.RedisKeys;
import com.recruit.gateway.config.GatewaySecurityProperties;
import com.recruit.gateway.ratelimit.RedisTokenBucketRateLimiter;
import com.recruit.gateway.ratelimit.RedisTokenBucketRateLimiter.Decision;
import com.recruit.gateway.support.GatewayErrorWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitFilter}：三层桶顺序、429 + Retry-After、Redis fail-closed。
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final String IP = "10.0.0.7";
    private static final String GLOBAL_KEY = RedisKeys.authRate("gw-global", "bucket");
    private static final String IP_KEY = RedisKeys.authRate("gw-ip", IP);
    private static final String LOGIN_KEY = RedisKeys.authRate("login", IP);

    @Mock
    private RedisTokenBucketRateLimiter rateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(rateLimiter, new GatewaySecurityProperties(),
                new GatewayErrorWriter(objectMapper));
    }

    @Test
    void allowedByAllBucketsPassesThrough() {
        when(rateLimiter.tryAcquire(GLOBAL_KEY, 200, 200.0)).thenReturn(Mono.just(new Decision(true, 0)));
        when(rateLimiter.tryAcquire(IP_KEY, 50, 50.0)).thenReturn(Mono.just(new Decision(true, 0)));
        MockServerWebExchange exchange = exchange("GET", "/api/interview/jobs");
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capture(downstream)).block();

        assertNotNull(downstream.get());
    }

    @Test
    void ipBucketExceededReturns429WithRetryAfter() throws Exception {
        when(rateLimiter.tryAcquire(GLOBAL_KEY, 200, 200.0)).thenReturn(Mono.just(new Decision(true, 0)));
        when(rateLimiter.tryAcquire(IP_KEY, 50, 50.0)).thenReturn(Mono.just(new Decision(false, 3)));
        MockServerWebExchange exchange = exchange("GET", "/api/interview/jobs");

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("GW9003", body(exchange).path("code").asText());
        assertEquals("3", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void loginPathAddsLoginBucket() {
        when(rateLimiter.tryAcquire(GLOBAL_KEY, 200, 200.0)).thenReturn(Mono.just(new Decision(true, 0)));
        when(rateLimiter.tryAcquire(IP_KEY, 50, 50.0)).thenReturn(Mono.just(new Decision(true, 0)));
        when(rateLimiter.tryAcquire(LOGIN_KEY, 100, 100.0 / 60.0))
                .thenReturn(Mono.just(new Decision(false, 1)));
        MockServerWebExchange exchange = exchange("POST", "/api/auth/login");

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        verify(rateLimiter).tryAcquire(LOGIN_KEY, 100, 100.0 / 60.0);
    }

    @Test
    void nonLoginRequestDoesNotConsumeLoginBucket() {
        when(rateLimiter.tryAcquire(GLOBAL_KEY, 200, 200.0)).thenReturn(Mono.just(new Decision(true, 0)));
        when(rateLimiter.tryAcquire(IP_KEY, 50, 50.0)).thenReturn(Mono.just(new Decision(true, 0)));
        MockServerWebExchange exchange = exchange("POST", "/api/interview/applications");

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        verify(rateLimiter, org.mockito.Mockito.never())
                .tryAcquire(org.mockito.ArgumentMatchers.eq(LOGIN_KEY),
                        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void redisFailureReturns503Gw9002() throws Exception {
        when(rateLimiter.tryAcquire(GLOBAL_KEY, 200, 200.0))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        MockServerWebExchange exchange = exchange("GET", "/api/interview/jobs");

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals("GW9002", body(exchange).path("code").asText());
    }

    @Test
    void actuatorHealthIsNotRateLimited() {
        MockServerWebExchange exchange = exchange("GET", "/actuator/health");
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capture(downstream)).block();

        assertNotNull(downstream.get());
        verifyNoInteractions(rateLimiter);
    }

    private static MockServerWebExchange exchange(String method, String path) {
        MockServerHttpRequest request = "POST".equals(method)
                ? MockServerHttpRequest.post(path).remoteAddress(new InetSocketAddress(IP, 12345)).build()
                : MockServerHttpRequest.get(path).remoteAddress(new InetSocketAddress(IP, 12345)).build();
        return MockServerWebExchange.from(request);
    }

    private static org.springframework.cloud.gateway.filter.GatewayFilterChain capture(
            AtomicReference<ServerWebExchange> ref) {
        return exchange -> {
            ref.set(exchange);
            return Mono.empty();
        };
    }

    private JsonNode body(MockServerWebExchange exchange) throws Exception {
        return objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
    }
}
