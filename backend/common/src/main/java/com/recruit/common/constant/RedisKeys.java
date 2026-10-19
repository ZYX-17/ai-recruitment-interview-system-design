package com.recruit.common.constant;

/**
 * Redis Key 契约（与开发文档 4.3 对齐）。所有 Key 由此处统一定义，禁止散落硬编码。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 刷新令牌（按 token hash）：auth:refresh:{sha256(token)} */
    public static final String AUTH_REFRESH = "auth:refresh:";
    /** 令牌族（重放检测）：auth:refresh:family:{familyId} */
    public static final String AUTH_REFRESH_FAMILY = "auth:refresh:family:";
    /** 黑名单（登出/踢线）：auth:bl:{jti} */
    public static final String AUTH_BLACKLIST = "auth:bl:";
    /** 令牌版本号：auth:tv:{userId} */
    public static final String AUTH_TOKEN_VERSION = "auth:tv:";
    /** 权限缓存：auth:perm:{userId}:{permVersion} */
    public static final String AUTH_PERM = "auth:perm:";

    /** 房间并发/名额锁：room:lock:{roomId} */
    public static final String ROOM_LOCK = "room:lock:";
    /** ASR 短时令牌（scope=asr，5min）：asr:token:{sessionId} */
    public static final String ASR_TOKEN = "asr:token:";

    /** 幂等占位（消费侧短兜底，DB 幂等表为准）：idem:{consumerGroup}:{eventId} */
    public static final String IDEMPOTENT = "idem:";
}
