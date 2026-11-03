package com.recruit.gateway.filter;

import com.recruit.common.context.UserContext;
import com.recruit.gateway.security.GatewayUser;
import com.recruit.gateway.support.GatewayAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link UserHeaderFilter}：S7 客户端伪造 {@code X-User-*} 必须被剥离。
 */
class UserHeaderFilterTest {

    private final UserHeaderFilter filter = new UserHeaderFilter();

    @Test
    void forgedUserHeadersAreStrippedForAnonymousRequests() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/portal/jobs")
                        .header(UserContext.HEADER_USER_ID, "99999")
                        .header(UserContext.HEADER_USER_ROLE, "SUPER_ADMIN")
                        .header("X-Internal-Token", "forged")
                        .build());
        HttpHeaders downstream = capture(exchange);

        assertNull(downstream.getFirst(UserContext.HEADER_USER_ID));
        assertNull(downstream.getFirst(UserContext.HEADER_USER_ROLE));
        assertNull(downstream.getFirst("X-Internal-Token"));
    }

    @Test
    void authenticatedUserHeadersAreInjected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/interview/jobs")
                        .header(UserContext.HEADER_USER_ID, "99999")
                        .header(UserContext.HEADER_USER_ROLE, "SUPER_ADMIN")
                        .build());
        exchange.getAttributes().put(GatewayAttributes.USER,
                new GatewayUser("10001", List.of("HR", "INTERVIEWER"), "jti-1", 3L));

        HttpHeaders downstream = capture(exchange);

        assertEquals("10001", downstream.getFirst(UserContext.HEADER_USER_ID));
        assertEquals("HR,INTERVIEWER", downstream.getFirst(UserContext.HEADER_USER_ROLE));
    }

    @Test
    void emptyRolesDoesNotEmitRoleHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/me").build());
        exchange.getAttributes().put(GatewayAttributes.USER,
                new GatewayUser("10001", List.of(), "jti-1", 3L));

        HttpHeaders downstream = capture(exchange);

        assertEquals("10001", downstream.getFirst(UserContext.HEADER_USER_ID));
        assertNull(downstream.getFirst(UserContext.HEADER_USER_ROLE));
    }

    private HttpHeaders capture(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            downstream.set(ex);
            return Mono.empty();
        }).block();
        return downstream.get().getRequest().getHeaders();
    }
}
