package com.recruit.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link TokenStateChecker}：黑名单、token_version、Redis 异常均 fail-closed。
 */
@ExtendWith(MockitoExtension.class)
class TokenStateCheckerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private TokenStateChecker checker;

    @BeforeEach
    void setUp() {
        checker = new TokenStateChecker(redisTemplate);
    }

    @Test
    void activeWhenNotBlacklistedAndVersionMatches() {
        stub("3");
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenReturn(Mono.just(false));

        assertTrue(checker.isActive(user()).block());
    }

    @Test
    void blacklistedTokenIsInactive() {
        stub("3");
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenReturn(Mono.just(true));

        assertFalse(checker.isActive(user()).block());
    }

    @Test
    void missingVersionFailsClosed() {
        stub(null);

        assertFalse(checker.isActive(user()).block());
    }

    @Test
    void versionMismatchIsInactive() {
        stub("2");

        assertFalse(checker.isActive(user()).block());
    }

    @Test
    void nonNumericVersionFailsClosed() {
        stub("not-a-number");

        assertFalse(checker.isActive(user()).block());
    }

    @Test
    void redisErrorIsPropagatedForFailClosedHandling() {
        when(redisTemplate.hasKey("auth:bl:jti-1"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn(Mono.just("3"));

        assertThrows(IllegalStateException.class, () -> checker.isActive(user()).block());
    }

    private void stub(String version) {
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenReturn(Mono.just(false));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001"))
                .thenReturn(version == null ? Mono.empty() : Mono.just(version));
    }

    private static GatewayUser user() {
        return new GatewayUser("10001", List.of("HR"), "jti-1", 3L);
    }
}
