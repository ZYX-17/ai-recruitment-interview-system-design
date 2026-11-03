package com.recruit.gateway.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 基于 Redis 的令牌桶限流（Lua 原子执行，多网关实例共享配额）。
 *
 * <p>Key 里用 Hash 保存 {@code tokens}/{@code ts}，每次请求按经过时间补充令牌，
 * 不足 1 个则拒绝并返回需要等待的毫秒数。脚本内同时刷新 TTL，避免冷 Key 永久堆积。</p>
 */
@Component
@RequiredArgsConstructor
public class RedisTokenBucketRateLimiter {

    private static final RedisScript<List> TOKEN_BUCKET = RedisScript.of("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local data = redis.call('HMGET', key, 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            local delta = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + delta * rate)
            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end
            redis.call('HSET', key, 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', key, ttl)
            local waitMs = 0
            if allowed == 0 then
              waitMs = math.ceil((1 - tokens) / rate * 1000)
            end
            return {allowed, waitMs}
            """, List.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 尝试获取一个令牌。
     *
     * @param key           限流 Key
     * @param capacity      桶容量
     * @param ratePerSecond 每秒补充令牌数
     * @return 是否放行及建议等待秒数
     */
    public Mono<Decision> tryAcquire(String key, int capacity, double ratePerSecond) {
        long now = System.currentTimeMillis();
        long ttlMs = Math.max(1000L, (long) Math.ceil(capacity / ratePerSecond * 2_000L));
        List<String> args = List.of(String.valueOf(capacity), String.valueOf(ratePerSecond),
                String.valueOf(now), String.valueOf(ttlMs));
        return redisTemplate.execute(TOKEN_BUCKET, List.of(key), args)
                .next()
                .map(result -> toDecision((List<?>) result));
    }

    private static Decision toDecision(List<?> values) {
        long allowed = ((Number) values.get(0)).longValue();
        long waitMs = ((Number) values.get(1)).longValue();
        return new Decision(allowed == 1L, Math.max(1L, (waitMs + 999L) / 1000L));
    }

    /**
     * 限流结果。
     *
     * @param allowed            是否放行
     * @param retryAfterSeconds  建议等待秒数（写入 Retry-After）
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }
}
