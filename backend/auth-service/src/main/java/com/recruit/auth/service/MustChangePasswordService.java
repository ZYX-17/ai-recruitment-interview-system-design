package com.recruit.auth.service;

import com.recruit.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 管理员重置密码后的“强制改密”标记（Redis）。
 *
 * <p>{@code iam_user} 没有 {@code must_change_password} 列，这里用带 TTL 的 Redis Key
 * 承载一次性标记：登录时下发给前端，自助改密成功后删除。</p>
 */
@Service
@RequiredArgsConstructor
public class MustChangePasswordService {

    /** 标记有效期；到期后允许正常登录，避免 Redis 残留永久锁死账号。 */
    public static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    /**
     * 标记用户下次登录必须改密。
     *
     * @param userId 用户 ID
     */
    public void mark(long userId) {
        redisTemplate.opsForValue().set(key(userId), "1", TTL);
    }

    /**
     * 是否被标记为必须改密。
     *
     * @param userId 用户 ID
     * @return 标记存在返回 true
     */
    public boolean isMarked(long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(userId)));
    }

    /**
     * 清除标记（改密成功后调用）。
     *
     * @param userId 用户 ID
     */
    public void clear(long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(long userId) {
        return RedisKeys.authMustChangePassword(Long.toString(userId));
    }
}
