package com.recruit.auth.service;

import com.recruit.auth.entity.IamRefreshSession;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamRefreshSessionMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.common.constant.RedisKeys;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 令牌失效与全端登出（P1-5）。
 *
 * <p>两条失效路径：</p>
 * <ul>
 *   <li>Access：当前 jti 写黑名单 {@code auth:bl:{jti}}，TTL 只到令牌自身过期；</li>
 *   <li>Refresh：按 DB 反查该用户 ACTIVE 会话，删 Redis 会话/令牌族，DB 置 REVOKED。</li>
 * </ul>
 *
 * <p>全端登出/改密/禁用统一递增 {@code iam_user.token_version}，并把新值回填 Redis
 * {@code auth:tv:{userId}}。过滤器与网关读不到 Key 或版本不一致都必须拒绝，不能默认 0。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    /** 会话撤销状态。 */
    public static final String STATUS_REVOKED = "REVOKED";

    private final StringRedisTemplate redisTemplate;
    private final IamRefreshSessionMapper refreshSessionMapper;
    private final IamUserMapper userMapper;

    /**
     * Access Token 入黑名单。
     *
     * @param jti       令牌唯一 ID
     * @param expiresAt 令牌过期时间；TTL 取剩余时间，已过期则不写
     */
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(RedisKeys.authBlacklist(jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 判断 Access Token 是否仍可用：未进黑名单，且 tv 与 Redis 一致。
     *
     * <p>Redis 异常按 fail-closed 返回 false，避免缓存故障时旧令牌复活（docs/4 §7.1.1）。</p>
     *
     * @param claims 已验签的 Access 声明
     * @return true 表示可继续使用
     */
    public boolean isAccessTokenActive(JwtClaims claims) {
        if (claims == null || claims.jti() == null || claims.jti().isBlank()
                || claims.userId() == null || claims.userId().isBlank()) {
            return false;
        }
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.authBlacklist(claims.jti())))) {
                return false;
            }
            String cachedVersion = redisTemplate.opsForValue().get(RedisKeys.authTokenVersion(claims.userId()));
            return cachedVersion != null && cachedVersion.equals(Long.toString(claims.tokenVersion()));
        } catch (RuntimeException e) {
            log.warn("令牌状态校验失败，按 fail-closed 拒绝: userId={}, jti={}", claims.userId(), claims.jti(), e);
            return false;
        }
    }

    /**
     * 撤销用户全部会话并递增 token_version。
     *
     * @param userId 用户 ID
     * @return 递增后的 token_version
     */
    @Transactional
    public long revokeAllSessions(long userId) {
        return revokeAllSessions(userId, STATUS_REVOKED);
    }

    /**
     * 撤销用户全部会话并递增 token_version（可指定会话状态）。
     *
     * @param userId 用户 ID
     * @param status 会话新状态，通常为 REVOKED
     * @return 递增后的 token_version
     */
    @Transactional
    public long revokeAllSessions(long userId, String status) {
        List<IamRefreshSession> sessions = refreshSessionMapper.selectActiveByUserId(userId);
        int updated = userMapper.incrementTokenVersion(userId);
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        IamUser user = userMapper.selectById(userId);
        long version = user == null || user.getTokenVersion() == null ? 0L : user.getTokenVersion();

        // 先写新版本：旧 Access/Refresh 从这一刻起即 fail-closed，再清理会话明细
        redisTemplate.opsForValue().set(RedisKeys.authTokenVersion(Long.toString(userId)), Long.toString(version));

        Set<String> familyIds = new LinkedHashSet<>();
        for (IamRefreshSession session : sessions) {
            redisTemplate.delete(RedisKeys.authRefresh(session.getTokenHash()));
            if (session.getFamilyId() != null && !session.getFamilyId().isBlank()) {
                familyIds.add(session.getFamilyId());
            }
        }
        for (String familyId : familyIds) {
            redisTemplate.delete(RedisKeys.authRefreshFamily(familyId));
        }
        refreshSessionMapper.revokeActiveByUserId(userId, status, LocalDateTime.now(ZoneOffset.UTC));
        return version;
    }
}
