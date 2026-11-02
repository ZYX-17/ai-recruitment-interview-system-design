package com.recruit.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.entity.IamRefreshSession;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamRefreshSessionMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.security.TokenType;
import com.recruit.auth.support.TokenHash;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RefreshSessionService} 的单元测试：Redis 只存哈希、DB 会话字段完整。
 */
@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

    @Mock
    private TokenService tokenService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private IamRefreshSessionMapper refreshSessionMapper;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;

    private RefreshSessionService service;

    @BeforeEach
    void setUp() {
        service = new RefreshSessionService(tokenService, redisTemplate, refreshSessionMapper, new ObjectMapper());
    }

    @Test
    void issueStoresHashedSessionInRedisAndDatabase() {
        IamUser user = new IamUser();
        user.setId(10001L);
        user.setUsername("hr01");
        List<String> roles = List.of("RECRUITER");
        when(tokenService.createRefreshToken(eq(user), eq(roles), eq(3L), anyString())).thenReturn("refresh-token");
        Instant now = Instant.now();
        when(tokenService.parseRefreshToken("refresh-token")).thenReturn(new JwtClaims(
                "10001", "hr01", roles, 3L, "jti-1", TokenType.REFRESH, "family-1",
                now, now.plusSeconds(604800)));
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        RefreshSessionService.IssuedRefresh issued =
                service.issue(user, roles, 3L, "10.0.0.1", "JUnit");

        assertEquals("refresh-token", issued.token());
        assertEquals("jti-1", issued.jti());
        assertNotNull(issued.familyId());

        String hash = TokenHash.sha256("refresh-token");
        verify(valueOperations).set(eq("auth:refresh:" + hash), contains("\"jti\":\"jti-1\""),
                eq(Duration.ofSeconds(604800)));
        verify(setOperations).add(startsWith("auth:refresh:family:"), eq("jti-1"));
        verify(redisTemplate).expire(startsWith("auth:refresh:family:"), eq(Duration.ofSeconds(604800)));

        ArgumentCaptor<IamRefreshSession> captor = ArgumentCaptor.forClass(IamRefreshSession.class);
        verify(refreshSessionMapper).insert(captor.capture());
        IamRefreshSession row = captor.getValue();
        assertEquals(10001L, row.getUserId());
        assertEquals(issued.familyId(), row.getFamilyId());
        assertEquals(hash, row.getTokenHash());
        assertEquals("ACTIVE", row.getStatus());
        assertEquals("10.0.0.1", row.getIp());
        assertEquals("JUnit", row.getDevice());
        assertTrue(row.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(6)));
    }

    @Test
    void rotateConsumesOldTokenAndIssuesNewTokenInSameFamily() {
        IamUser user = activeUser();
        List<String> roles = List.of("RECRUITER");
        JwtClaims claims = refreshClaims("jti-old");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn("3");
        when(redisTemplate.hasKey("auth:refresh:family:family-1")).thenReturn(true);
        String oldHash = TokenHash.sha256("old-token");
        when(valueOperations.getAndDelete("auth:refresh:" + oldHash))
                .thenReturn(sessionJson("jti-old", "ACTIVE"));
        when(tokenService.createRefreshToken(user, roles, 3L, "family-1")).thenReturn("new-token");
        when(tokenService.parseRefreshToken("new-token")).thenReturn(refreshClaims("jti-new"));
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        RefreshSessionService.IssuedRefresh issued =
                service.rotate("old-token", claims, user, roles, 3L, "10.0.0.1", "JUnit");

        assertEquals("new-token", issued.token());
        assertEquals("jti-new", issued.jti());
        verify(refreshSessionMapper).updateStatusByTokenHash(oldHash, "CONSUMED", null);
        verify(valueOperations).set(eq("auth:refresh:" + TokenHash.sha256("new-token")),
                contains("jti-new"), eq(Duration.ofSeconds(604800)));
        verify(setOperations).add("auth:refresh:family:family-1", "jti-new");
    }

    @Test
    void replayedTokenRevokesWholeFamily() {
        IamUser user = activeUser();
        JwtClaims claims = refreshClaims("jti-old");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn("3");
        when(redisTemplate.hasKey("auth:refresh:family:family-1")).thenReturn(true);
        when(valueOperations.getAndDelete("auth:refresh:" + TokenHash.sha256("old-token"))).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> service.rotate("old-token", claims, user, List.of("RECRUITER"), 3L, "10.0.0.1", "JUnit"));

        assertEquals(ErrorCode.AUTH_REFRESH_REPLAYED, exception.getErrorCode());
        verify(redisTemplate).delete("auth:refresh:family:family-1");
        verify(refreshSessionMapper).updateStatusByFamilyId(eq("family-1"), eq("REPLAYED"), any());
    }

    @Test
    void revokedFamilyIsRejectedWithoutConsumingToken() {
        IamUser user = activeUser();
        JwtClaims claims = refreshClaims("jti-old");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn("3");
        when(redisTemplate.hasKey("auth:refresh:family:family-1")).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.rotate("old-token", claims, user, List.of("RECRUITER"), 3L, "10.0.0.1", "JUnit"));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
        verify(valueOperations, never()).getAndDelete(anyString());
    }

    @Test
    void missingRedisTokenVersionFailsClosedAndRevokesFamily() {
        IamUser user = activeUser();
        JwtClaims claims = refreshClaims("jti-old");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> service.rotate("old-token", claims, user, List.of("RECRUITER"), 3L, "10.0.0.1", "JUnit"));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
        verify(redisTemplate).delete("auth:refresh:family:family-1");
        verify(refreshSessionMapper).updateStatusByFamilyId(eq("family-1"), eq("REVOKED"), any());
    }

    @Test
    void changedTokenVersionRevokesFamily() {
        IamUser user = activeUser();
        JwtClaims claims = refreshClaims("jti-old");

        BizException exception = assertThrows(BizException.class,
                () -> service.rotate("old-token", claims, user, List.of("RECRUITER"), 4L, "10.0.0.1", "JUnit"));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
        verify(refreshSessionMapper).updateStatusByFamilyId(eq("family-1"), eq("REVOKED"), any());
    }

    @Test
    void mismatchedSessionPayloadRevokesFamily() {
        IamUser user = activeUser();
        JwtClaims claims = refreshClaims("jti-old");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:tv:10001")).thenReturn("3");
        when(redisTemplate.hasKey("auth:refresh:family:family-1")).thenReturn(true);
        when(valueOperations.getAndDelete("auth:refresh:" + TokenHash.sha256("old-token")))
                .thenReturn(sessionJson("other-jti", "ACTIVE"));

        BizException exception = assertThrows(BizException.class,
                () -> service.rotate("old-token", claims, user, List.of("RECRUITER"), 3L, "10.0.0.1", "JUnit"));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
        verify(refreshSessionMapper).updateStatusByFamilyId(eq("family-1"), eq("REVOKED"), any());
    }

    private static IamUser activeUser() {
        IamUser user = new IamUser();
        user.setId(10001L);
        user.setUsername("hr01");
        user.setStatus("ACTIVE");
        user.setTokenVersion(3);
        return user;
    }

    private static JwtClaims refreshClaims(String jti) {
        Instant now = Instant.now();
        return new JwtClaims("10001", "hr01", List.of("RECRUITER"), 3L, jti,
                TokenType.REFRESH, "family-1", now, now.plusSeconds(604800));
    }

    private static String sessionJson(String jti, String status) {
        return "{\"userId\":\"10001\",\"familyId\":\"family-1\",\"jti\":\"" + jti
                + "\",\"status\":\"" + status + "\"}";
    }
}
