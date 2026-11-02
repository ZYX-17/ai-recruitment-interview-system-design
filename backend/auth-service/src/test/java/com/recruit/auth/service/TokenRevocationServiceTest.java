package com.recruit.auth.service;

import com.recruit.auth.entity.IamRefreshSession;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamRefreshSessionMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.security.TokenType;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TokenRevocationService} 的黑名单、token_version 与全端撤销测试。
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private IamRefreshSessionMapper refreshSessionMapper;
    @Mock
    private IamUserMapper userMapper;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(redisTemplate, refreshSessionMapper, userMapper);
    }

    @Test
    void activeWhenNotBlacklistedAndVersionMatches() {
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn("3");

        assertTrue(service.isAccessTokenActive(claims("jti-1", 3L)));
    }

    @Test
    void blacklistedTokenIsInactive() {
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenReturn(true);

        assertFalse(service.isAccessTokenActive(claims("jti-1", 3L)));
    }

    @Test
    void missingTokenVersionFailsClosed() {
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn(null);

        assertFalse(service.isAccessTokenActive(claims("jti-1", 3L)));
    }

    @Test
    void versionMismatchIsInactive() {
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn("2");

        assertFalse(service.isAccessTokenActive(claims("jti-1", 3L)));
    }

    @Test
    void redisFailureFailsClosed() {
        when(redisTemplate.hasKey("auth:bl:jti-1")).thenThrow(new IllegalStateException("redis down"));

        assertFalse(service.isAccessTokenActive(claims("jti-1", 3L)));
    }

    @Test
    void blacklistUsesRemainingTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service.blacklist("jti-1", Instant.now().plusSeconds(60));

        verify(valueOperations).set(eq("auth:bl:jti-1"), eq("1"),
                argThat(ttl -> ttl.getSeconds() > 0 && ttl.getSeconds() <= 60));
    }

    @Test
    void expiredTokenIsNotBlacklisted() {
        service.blacklist("jti-1", Instant.now().minusSeconds(10));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void revokeAllSessionsDeletesRefreshKeysAndBumpsVersion() {
        IamRefreshSession first = session("hash-1", "family-1");
        IamRefreshSession second = session("hash-2", "family-1");
        IamRefreshSession third = session("hash-3", "family-2");
        when(refreshSessionMapper.selectActiveByUserId(10001L)).thenReturn(List.of(first, second, third));
        when(userMapper.incrementTokenVersion(10001L)).thenReturn(1);
        IamUser user = new IamUser();
        user.setTokenVersion(4);
        when(userMapper.selectById(10001L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        long version = service.revokeAllSessions(10001L);

        assertEquals(4L, version);
        verify(redisTemplate).delete("auth:refresh:hash-1");
        verify(redisTemplate).delete("auth:refresh:hash-2");
        verify(redisTemplate).delete("auth:refresh:hash-3");
        verify(redisTemplate).delete("auth:refresh:family:family-1");
        verify(redisTemplate).delete("auth:refresh:family:family-2");
        verify(valueOperations).set("auth:tv:10001", "4");
        verify(refreshSessionMapper).revokeActiveByUserId(eq(10001L), eq("REVOKED"), any(LocalDateTime.class));
    }

    @Test
    void revokeAllSessionsRejectsUnknownUser() {
        when(refreshSessionMapper.selectActiveByUserId(10001L)).thenReturn(List.of());
        when(userMapper.incrementTokenVersion(10001L)).thenReturn(0);

        BizException exception = assertThrows(BizException.class, () -> service.revokeAllSessions(10001L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(redisTemplate, never()).opsForValue();
    }

    private static IamRefreshSession session(String hash, String familyId) {
        IamRefreshSession session = new IamRefreshSession();
        session.setTokenHash(hash);
        session.setFamilyId(familyId);
        return session;
    }

    private static JwtClaims claims(String jti, long tokenVersion) {
        Instant now = Instant.now();
        return new JwtClaims("10001", "hr01", List.of("RECRUITER"), tokenVersion, jti,
                TokenType.ACCESS, null, now, now.plusSeconds(600));
    }
}
