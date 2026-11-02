package com.recruit.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.entity.IamRefreshSession;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamRefreshSessionMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.support.TokenHash;
import com.recruit.common.constant.RedisKeys;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
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
 * Refresh 会话：登录签发、刷新轮换、family 撤销。
 *
 * <p>主存储在 Redis，DB {@code iam_refresh_session} 只做会话登记与审计：</p>
 * <ul>
 *   <li>{@code auth:refresh:{sha256(token)}} → 会话 JSON（userId/familyId/jti/status）；</li>
 *   <li>{@code auth:refresh:family:{familyId}} → jti 集合，撤销时删 Key 即整族失效；</li>
 *   <li>轮换必须 {@code GETDEL} 原子消费；旧 token 再次出现即判定重放，撤销整族。</li>
 * </ul>
 *
 * <p>并发刷新同一 token：只有一个能拿到 GETDEL 结果，另一个按重放处理（docs/4 §7.1.3）；
 * 前端应做单飞刷新队列。</p>
 */
@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_REPLAYED = "REPLAYED";

    private final TokenService tokenService;
    private final StringRedisTemplate redisTemplate;
    private final IamRefreshSessionMapper refreshSessionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 登录签发：新建 family 与 Refresh Token。
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
        return createSession(user, roles, tokenVersion, UUID.randomUUID().toString(), ip, device);
    }

    /**
     * 刷新轮换：消费旧 token，签发同族新 token。
     *
     * <p>任一校验失败都 fail-closed：旧 token 已被 GETDEL 消费，客户端需重新登录。</p>
     *
     * @param oldToken     旧 Refresh Token
     * @param claims       旧 token 的已验签声明
     * @param user         用户
     * @param roles        角色码
     * @param tokenVersion DB 中的令牌版本
     * @param ip           客户端 IP
     * @param device       User-Agent 摘要
     * @return 新令牌
     */
    @Transactional
    public IssuedRefresh rotate(String oldToken, JwtClaims claims, IamUser user, List<String> roles,
                                long tokenVersion, String ip, String device) {
        String userId = String.valueOf(user.getId());
        String familyId = claims.familyId();
        if (familyId == null || familyId.isBlank()) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        if (!STATUS_ACTIVE.equals(user.getStatus()) || claims.tokenVersion() != tokenVersion) {
            revokeFamily(familyId, STATUS_REVOKED);
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        String cachedVersion = redisTemplate.opsForValue().get(RedisKeys.authTokenVersion(userId));
        if (cachedVersion == null || !cachedVersion.equals(Long.toString(tokenVersion))) {
            revokeFamily(familyId, STATUS_REVOKED);
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        String familyKey = RedisKeys.AUTH_REFRESH_FAMILY + familyId;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(familyKey))) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        String tokenHash = TokenHash.sha256(oldToken);
        String payload = redisTemplate.opsForValue().getAndDelete(RedisKeys.AUTH_REFRESH + tokenHash);
        if (payload == null) {
            // 旧 token 已被消费仍再次出现：family 还在，判定重放，整族撤销
            revokeFamily(familyId, STATUS_REPLAYED);
            throw new BizException(ErrorCode.AUTH_REFRESH_REPLAYED);
        }

        JsonNode session = readSession(payload);
        if (!STATUS_ACTIVE.equals(session.path("status").asText())
                || !familyId.equals(session.path("familyId").asText())
                || !userId.equals(session.path("userId").asText())
                || claims.jti() == null
                || !claims.jti().equals(session.path("jti").asText())) {
            revokeFamily(familyId, STATUS_REVOKED);
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        // 消费后再确认一次 family：并发登出可能刚好删掉整族
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(familyKey))) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        refreshSessionMapper.updateStatusByTokenHash(tokenHash, STATUS_CONSUMED, null);
        return createSession(user, roles, tokenVersion, familyId, ip, device);
    }

    /**
     * 撤销整个令牌族（重放、登出、禁用）。
     *
     * @param familyId 令牌族
     * @param status   REVOKED / REPLAYED
     */
    @Transactional
    public void revokeFamily(String familyId, String status) {
        if (familyId == null || familyId.isBlank()) {
            return;
        }
        redisTemplate.delete(RedisKeys.AUTH_REFRESH_FAMILY + familyId);
        refreshSessionMapper.updateStatusByFamilyId(familyId, status, LocalDateTime.now(ZoneOffset.UTC));
    }

    private IssuedRefresh createSession(IamUser user, List<String> roles, long tokenVersion,
                                        String familyId, String ip, String device) {
        String token = tokenService.createRefreshToken(user, roles, tokenVersion, familyId);
        JwtClaims claims = tokenService.parseRefreshToken(token);
        String tokenHash = TokenHash.sha256(token);
        String userId = String.valueOf(user.getId());
        long ttlSeconds = tokenService.refreshTtlSeconds();
        Duration ttl = Duration.ofSeconds(ttlSeconds);

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("userId", userId);
        session.put("familyId", familyId);
        session.put("jti", claims.jti());
        session.put("status", STATUS_ACTIVE);
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
        row.setStatus(STATUS_ACTIVE);
        refreshSessionMapper.insert(row);

        return new IssuedRefresh(token, familyId, claims.jti());
    }

    private JsonNode readSession(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }
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
     * 签发结果。
     *
     * @param token    Refresh Token 原文（只进 Cookie，不落库）
     * @param familyId 令牌族 ID
     * @param jti      令牌唯一 ID
     */
    public record IssuedRefresh(String token, String familyId, String jti) {
    }
}
