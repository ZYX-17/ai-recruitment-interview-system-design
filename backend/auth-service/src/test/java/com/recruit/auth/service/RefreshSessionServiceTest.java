package com.recruit.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.entity.IamRefreshSession;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamRefreshSessionMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.security.TokenType;
import com.recruit.auth.support.TokenHash;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
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
}
