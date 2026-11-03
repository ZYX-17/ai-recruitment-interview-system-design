package com.recruit.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.constant.RedisKeys;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import com.recruit.interview.client.AuthPermissionClient;
import com.recruit.interview.dto.AuthzSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 权限码读取：先读 auth-service 维护的 {@code auth:perm:{uid}:{ver}} 缓存，
 * 未命中再走内部接口回源并回写（TTL 30min）。
 *
 * <p>任何失败都不放行：调用方 {@code PermissionChecker} 会把异常转成 false（403）。
 * 与 auth-service P2-6 使用同一 Key 契约，避免两套缓存不一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AuthPermissionClient authPermissionClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询用户权限码。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    public List<String> permissionCodes(long userId) {
        String userIdText = String.valueOf(userId);
        String version = readVersion(userIdText);
        String cacheKey = RedisKeys.authPerm(userIdText, parseVersion(version));
        List<String> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        AuthzSnapshot snapshot = loadAuthz(userId);
        writeCache(cacheKey, snapshot.permissions());
        return snapshot.permissions();
    }

    /**
     * 查询完整鉴权快照（权限码 + 角色 + 数据范围）。
     *
     * <p>数据范围每次实时取，不缓存（docs/4 §7.2.2）。</p>
     *
     * @param userId 用户 ID
     * @return 鉴权快照
     */
    public AuthzSnapshot authz(long userId) {
        return loadAuthz(userId);
    }

    private AuthzSnapshot loadAuthz(long userId) {
        Result<AuthzSnapshot> result = authPermissionClient.authz(userId);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "鉴权服务不可用");
        }
        return result.getData();
    }

    private String readVersion(String userId) {
        try {
            String version = redisTemplate.opsForValue().get(RedisKeys.authPermVersion(userId));
            return version == null || version.isBlank() ? "0" : version;
        } catch (RuntimeException e) {
            log.warn("读取权限版本失败，按 0 处理并回源鉴权服务: userId={}", userId, e);
            return "0";
        }
    }

    private List<String> readCache(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            return json == null ? null : objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            log.warn("读取权限缓存失败，回源鉴权服务: key={}", cacheKey, e);
            return null;
        }
    }

    private void writeCache(String cacheKey, List<String> codes) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(codes), CACHE_TTL);
        } catch (Exception e) {
            log.warn("写权限缓存失败（不影响本次鉴权结果）: key={}", cacheKey, e);
        }
    }

    private static long parseVersion(String version) {
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
