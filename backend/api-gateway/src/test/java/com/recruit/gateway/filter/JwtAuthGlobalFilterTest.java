package com.recruit.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.gateway.security.GatewayJwtVerifier;
import com.recruit.gateway.security.GatewayUser;
import com.recruit.gateway.security.TokenStateChecker;
import com.recruit.gateway.support.GatewayAttributes;
import com.recruit.gateway.support.GatewayErrorWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthGlobalFilter}：白名单、401、黑名单/版本失效、Redis fail-closed。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthGlobalFilterTest {

    @Mock
    private GatewayJwtVerifier jwtVerifier;
    @Mock
    private TokenStateChecker tokenStateChecker;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtAuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthGlobalFilter(jwtVerifier, tokenStateChecker,
                new GatewayErrorWriter(objectMapper));
    }

    @Test
    void loginWhitelistSkipsVerification() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capture(downstream)).block();

        assertNotNull(downstream.get());
        verifyNoInteractions(jwtVerifier, tokenStateChecker);
    }

    @Test
    void portalOfferWhitelistSkipsVerification() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/portal/offers/token-abc").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capture(downstream)).block();

        assertNotNull(downstream.get());
        verifyNoInteractions(jwtVerifier, tokenStateChecker);
    }

    @Test
    void portalApplicationsStillRequireLogin() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/portal/applications").build());

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("GW9001", body(exchange).path("code").asText());
    }

    @Test
    void anonymousProtectedRequestReturns401() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs").build());

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("GW9001", body(exchange).path("code").asText());
    }

    @Test
    void validTokenPassesAndStoresUserInExchange() {
        GatewayUser user = new GatewayUser("10001", List.of("HR"), "jti-1", 3L);
        when(jwtVerifier.verifyAccessToken("token")).thenReturn(user);
        when(tokenStateChecker.isActive(user)).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs")
                        .header("Authorization", "Bearer token").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, capture(downstream)).block();

        assertNotNull(downstream.get());
        assertSame(user, downstream.get().getAttribute(GatewayAttributes.USER));
    }

    @Test
    void revokedTokenReturns401() throws Exception {
        GatewayUser user = new GatewayUser("10001", List.of("HR"), "jti-1", 3L);
        when(jwtVerifier.verifyAccessToken("token")).thenReturn(user);
        when(tokenStateChecker.isActive(user)).thenReturn(Mono.just(false));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs")
                        .header("Authorization", "Bearer token").build());

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("GW9001", body(exchange).path("code").asText());
        assertNull(exchange.getAttribute(GatewayAttributes.USER));
    }

    @Test
    void refreshTokenIsRejectedAtVerifier() throws Exception {
        when(jwtVerifier.verifyAccessToken("refresh-token"))
                .thenThrow(new IllegalArgumentException("令牌类型不是 access"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs")
                        .header("Authorization", "Bearer refresh-token").build());

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("GW9001", body(exchange).path("code").asText());
        verify(tokenStateChecker, never()).isActive(any());
    }

    @Test
    void redisFailureReturns503Gw9002() throws Exception {
        GatewayUser user = new GatewayUser("10001", List.of("HR"), "jti-1", 3L);
        when(jwtVerifier.verifyAccessToken("token")).thenReturn(user);
        when(tokenStateChecker.isActive(user))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs")
                        .header("Authorization", "Bearer token").build());

        filter.filter(exchange, capture(new AtomicReference<>())).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals("GW9002", body(exchange).path("code").asText());
    }

    @Test
    void ordersBeforeUserHeaderAndRateLimit() {
        int jwtOrder = filter.getOrder();

        assertEquals(new UserHeaderFilter().getOrder() - 10, jwtOrder);
    }

    private static GatewayFilterChain capture(AtomicReference<ServerWebExchange> ref) {
        return exchange -> {
            ref.set(exchange);
            return Mono.empty();
        };
    }

    private JsonNode body(MockServerWebExchange exchange) throws Exception {
        String json = exchange.getResponse().getBodyAsString().block();
        return objectMapper.readTree(json);
    }
}
