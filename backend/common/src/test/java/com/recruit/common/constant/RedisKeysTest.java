package com.recruit.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RedisKeys} 的单元测试：确认前缀拼接和参数校验符合 §4.3 契约。
 */
class RedisKeysTest {

    /**
     * 权限版本与权限缓存的 Key 格式必须是 auth:permver:{uid} / auth:perm:{uid}:{ver}。
     */
    @Test
    void buildsAuthPermissionKeys() {
        assertEquals("auth:permver:10001", RedisKeys.authPermVersion("10001"));
        assertEquals("auth:perm:10001:3", RedisKeys.authPerm("10001", 3));
    }

    /**
     * 限流 Key 的业务值允许带冒号（IPv6 地址），scope 不允许。
     */
    @Test
    void buildsRateKeyAndAllowsIpv6() {
        assertEquals("auth:rate:login:1.2.3.4", RedisKeys.authRate("login", "1.2.3.4"));
        assertEquals("auth:rate:login:2001:db8::1", RedisKeys.authRate("login", "2001:db8::1"));
        assertThrows(IllegalArgumentException.class, () -> RedisKeys.authRate("bad:scope", "x"));
    }

    /**
     * HTTP 幂等 Key 必须带独立前缀，避免和消费幂等 Key 冲突。
     */
    @Test
    void buildsHttpIdempotencyKey() {
        assertEquals("idem:http:interview.schedule:uuid-1",
                RedisKeys.idempotentHttp("interview.schedule", "uuid-1"));
        assertThrows(IllegalArgumentException.class,
                () -> RedisKeys.idempotentHttp("interview.schedule", " "));
    }
}
