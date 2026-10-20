package com.recruit.common.constant;

/**
 * Redis Key 契约（与开发文档 §4.3 对齐）。
 *
 * <p>为什么要集中定义：Redis Key 一旦散落在各个 Service 里，改一个前缀就要全仓库搜索，
 * 而且很容易出现 {@code auth:perm:} 和 {@code auth:permver:} 这类前缀互相误匹配的问题。
 * 这里统一成“常量前缀 + 拼接方法”，业务代码不再手写字符串。</p>
 *
 * <p>命名规则：{@code 模块:用途:{业务标识}}，用冒号分层，便于在 redis-cli 里按前缀扫描。</p>
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    // ==================== 认证 / 权限 ====================

    /** 刷新令牌（按 token hash）：auth:refresh:{sha256(token)} */
    public static final String AUTH_REFRESH = "auth:refresh:";

    /** 令牌族（重放检测）：auth:refresh:family:{familyId} */
    public static final String AUTH_REFRESH_FAMILY = "auth:refresh:family:";

    /** 黑名单（登出/踢线）：auth:bl:{jti} */
    public static final String AUTH_BLACKLIST = "auth:bl:";

    /** 令牌版本号：auth:tv:{userId} */
    public static final String AUTH_TOKEN_VERSION = "auth:tv:";

    /**
     * 权限版本号：auth:permver:{userId}。
     *
     * <p>角色/权限变更时递增，权限缓存 Key 里带版本号，版本一变旧缓存自然失效。
     * 文档 §7.2.2 要求该 Key 必须存在；读取时缺失按 0 处理。</p>
     */
    public static final String AUTH_PERM_VERSION = "auth:permver:";

    /** 权限缓存：auth:perm:{userId}:{permVersion}，TTL 30 分钟。 */
    public static final String AUTH_PERM = "auth:perm:";

    /**
     * 限流计数：auth:rate:{scope}:{key}。
     *
     * <p>scope 表示限流维度（如 login、sms、refresh），key 表示具体对象
     * （如 IP、用户名、手机号）。TTL 由调用方按窗口设置。</p>
     */
    public static final String AUTH_RATE = "auth:rate:";

    // ==================== 房间 ====================

    /** 房间并发/名额锁：room:lock:{roomId} */
    public static final String ROOM_LOCK = "room:lock:";

    /** ASR 短时令牌（scope=asr，5min）：asr:token:{sessionId} */
    public static final String ASR_TOKEN = "asr:token:";

    // ==================== 幂等 ====================

    /**
     * 消息消费幂等占位：idem:{consumerGroup}:{eventId}。
     *
     * <p>仅作为短兜底，最终以数据库 {@code idempotent_consumption} 唯一键为准。</p>
     */
    public static final String IDEMPOTENT = "idem:";

    /**
     * HTTP 写接口幂等：idem:http:{scope}:{idempotencyKey}。
     *
     * <p>与消费幂等分开前缀，避免客户端传入的 Idempotency-Key 恰好等于某个
     * consumerGroup/eventId 时互相覆盖。</p>
     */
    public static final String IDEMPOTENT_HTTP = "idem:http:";

    // ==================== Key 拼接方法 ====================

    /**
     * 构造权限版本 Key。
     *
     * @param userId 用户 ID，不能为空
     * @return auth:permver:{userId}
     */
    public static String authPermVersion(String userId) {
        return AUTH_PERM_VERSION + requireSegment(userId, "userId");
    }

    /**
     * 构造权限缓存 Key。
     *
     * @param userId 用户 ID，不能为空
     * @param version 权限版本号，缺失时传 0
     * @return auth:perm:{userId}:{version}
     */
    public static String authPerm(String userId, long version) {
        return AUTH_PERM + requireSegment(userId, "userId") + ":" + version;
    }

    /**
     * 构造限流 Key。
     *
     * @param scope 限流维度，例如 login / sms
     * @param key   限流对象，例如 IP、用户名
     * @return auth:rate:{scope}:{key}
     */
    public static String authRate(String scope, String key) {
        return AUTH_RATE + requireSegment(scope, "scope") + ":" + requireText(key, "key");
    }

    /**
     * 构造 HTTP 幂等 Key。
     *
     * @param scope          业务范围，例如 interview.schedule
     * @param idempotencyKey 客户端传入的 Idempotency-Key
     * @return idem:http:{scope}:{idempotencyKey}
     */
    public static String idempotentHttp(String scope, String idempotencyKey) {
        return IDEMPOTENT_HTTP + requireSegment(scope, "scope") + ":" + requireText(idempotencyKey, "idempotencyKey");
    }

    /**
     * 校验 Key 的业务值：不能为空，但允许包含冒号。
     *
     * <p>典型场景：限流 key 可能是 IPv6 地址（{@code 2001:db8::1}），
     * 冒号是正常字符，不能一棍子打死。</p>
     *
     * @param value 业务值
     * @param name  字段名，用于报错
     * @return 原值
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    /**
     * 校验 Key 的层级片段（scope、userId 等）：不能为空，也不能包含冒号，
     * 否则会把预期的层级结构打乱。
     *
     * @param value 片段值
     * @param name  字段名，用于报错
     * @return 原值
     */
    private static String requireSegment(String value, String name) {
        String text = requireText(value, name);
        if (text.indexOf(':') >= 0) {
            throw new IllegalArgumentException(name + " 不能包含冒号");
        }
        return text;
    }
}
