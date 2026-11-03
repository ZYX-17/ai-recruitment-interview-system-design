package com.recruit.gateway.security;

import com.recruit.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Access Token 运行态校验：黑名单 {@code auth:bl:{jti}} 与版本 {@code auth:tv:{uid}}。
 *
 * <p>两者任一不满足都拒绝（fail-closed）：黑名单存在说明已登出/踢线；
 * 版本缺失或与令牌不一致说明账号禁用、改密或全端登出。Redis 异常由调用方
 * 转成 503，绝不降级放行。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenStateChecker {

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 校验令牌是否仍然有效。
     *
     * @param user 已验签的用户快照
     * @return true 表示可放行；false 表示黑名单/版本不匹配
     */
    public Mono<Boolean> isActive(GatewayUser user) {
        Mono<Boolean> blacklisted = redisTemplate.hasKey(RedisKeys.authBlacklist(user.jti()));
        Mono<String> currentVersion = redisTemplate.opsForValue()
                .get(RedisKeys.authTokenVersion(user.userId()))
                .defaultIfEmpty("");
        return Mono.zip(blacklisted, currentVersion)
                .map(tuple -> !Boolean.TRUE.equals(tuple.getT1())
                        && user.tokenVersion() == parseVersion(tuple.getT2()))
                .defaultIfEmpty(false);
    }

    private static long parseVersion(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
