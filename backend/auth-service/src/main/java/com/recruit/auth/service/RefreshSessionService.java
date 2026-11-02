package com.recruit.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.entity.IamRefreshSession;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamRefreshSessionMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.support.TokenHash;
import com.recruit.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Refresh 会话登记（登录签发；轮换/重放检测在 P1-4）。
 *
 * <p>三处落点必须一致：Redis {@code auth:refresh:{sha256}} 保存会话 JSON、
 * {@code auth:refresh:family:{familyId}} 保存 jti 集合、DB {@code iam_refresh_session}
 * 保存会话登记。Redis 只存哈希，不存令牌原文。</p>
 */
@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;
    private final IamRefreshSessionMapper refreshSessionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 签发 Refresh Token 并登记会话。
     *
     * @param user         用户
     * @param roles        角色码
     * @param tokenVersion 令牌版本
     * @param ip           客户端 IP
     * @param device       User-Agent 摘要
     * @return 新令牌与会话标识
     */
    @Transactional
    public IssuedRefresh issue(IamUser user, List<String> roles, long tokenVersion, String ip, String device) {
        String familyId = UUID.randomUUID().toString();
        String token = tokenService.createRefreshToken(user, roles, tokenVersion, familyId);
        JwtClaims claims = tokenService.parseRefreshToken(token);
        String tokenHash = TokenHash.sha256(token);
        long ttlSeconds = tokenService.refreshTtlSeconds();
        Duration ttl = Duration.ofSeconds(ttlSeconds);

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("userId", String.valueOf(user.getId()));
        session.put("familyId", familyId);
        session.put("jti", claims.jti());
        session.put("status", "ACTIVE");
        redisTemplate.opsForValue().set(RedisKeys.AUTH_REFRESH + tokenHash, toJson(session), ttl);

        String familyKey = RedisKeys.AUTH_REFRESH_FAMILY + familyId;
        redisTemplate.opsForSet().add(familyKey, claims.jti());
        redisTemplate.expire(familyKey, ttl);

        IamRefreshSession row = new IamRefreshSession();
        row.setUserId(user.getId());
        row.setFamilyId(familyId);
        row.setTokenHash(tokenHash);
        row.setDevice(truncate(device, 255));
        row.setIp(truncate(ip, 64));
        row.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(ttlSeconds));
        row.setStatus("ACTIVE");
        refreshSessionMapper.insert(row);

        return new IssuedRefresh(token, familyId, claims.jti());
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Refresh 会话序列化失败", e);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 登录签发结果。
     *
     * @param token    Refresh Token 原文（只进 Cookie，不落库）
     * @param familyId 令牌族 ID
     * @param jti      令牌唯一 ID
     */
    public record IssuedRefresh(String token, String familyId, String jti) {
    }
}
