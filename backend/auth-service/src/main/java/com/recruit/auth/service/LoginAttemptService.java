package com.recruit.auth.service;

import com.recruit.auth.config.SecurityProperties;
import com.recruit.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败计数与锁定（Redis）。
 *
 * <p>口径（docs/4 §5.2.1）：连续失败 5 次锁定 15 分钟，锁定期间统一返回
 * {@code AUTH1001}，不区分“用户不存在/密码错误/已锁定”。</p>
 *
 * <p>Key 为 {@code auth:login:fail:{username}}，每次失败刷新 TTL，形成滑动窗口；
 * 脏值按锁定处理（fail-closed）。Redis 不可用时异常上抛，由全局处理器返回 500。</p>
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;

    /**
     * 是否已锁定。
     *
     * @param username 登录名
     * @return 连续失败次数达到阈值时返回 true
     */
    public boolean isLocked(String username) {
        String value = redisTemplate.opsForValue().get(RedisKeys.authLoginFail(username));
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= securityProperties.getLogin().getMaxFailures();
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * 记录一次失败并续期锁定窗口。
     *
     * @param username 登录名
     * @return 当前连续失败次数
     */
    public long recordFailure(String username) {
        String key = RedisKeys.authLoginFail(username);
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(securityProperties.getLogin().getLockMinutes()));
        return count == null ? securityProperties.getLogin().getMaxFailures() : count;
    }

    /**
     * 登录成功后清零。
     *
     * @param username 登录名
     */
    public void reset(String username) {
        redisTemplate.delete(RedisKeys.authLoginFail(username));
    }
}
