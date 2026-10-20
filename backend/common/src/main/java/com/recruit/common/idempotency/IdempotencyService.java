package com.recruit.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.constant.RedisKeys;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * HTTP 写接口幂等服务：用 Redis 做“占位 + 结果缓存”，保证同一个 Idempotency-Key 只执行一次。
 *
 * <p><b>解决什么问题：</b>用户网络抖动或前端重试时，同一个“提交面试结果”请求可能到达两次。
 * 如果没有幂等控制，会重复写库、重复发事件、重复通知。本服务让第二次请求直接拿到第一次的结果，
 * 不再执行业务逻辑。</p>
 *
 * <p><b>执行流程：</b></p>
 * <ol>
 *   <li>用 {@code SET key value NX EX 10min} 抢占 Key；</li>
 *   <li>抢到：执行业务逻辑，成功后把返回值序列化成 JSON 写回 Key；</li>
 *   <li>没抢到：读取 Key。若还在处理中，返回 409；若已有结果，反序列化后直接返回；</li>
 *   <li>业务抛异常：删除占位，允许客户端用同一个 Key 重试。</li>
 * </ol>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * String key = request.getHeader("Idempotency-Key");
 * return idempotencyService.execute("interview.result", key, ResultVO.class,
 *         () -> interviewService.submitResult(interviewId, command));
 * </pre>
 *
 * <p><b>边界说明：</b></p>
 * <ul>
 *   <li>幂等键由客户端生成（建议 UUID），服务端不替客户端生成，否则失去“跨重试识别”的意义；</li>
 *   <li>只缓存“正常返回”的结果。业务失败请抛 {@link BizException}，这样占位会被释放，用户改正参数后可重试；</li>
 *   <li>Redis 不可用时降级为直接执行并打 WARN：可用性优先，但此时幂等失效，
 *       关键写操作仍必须依赖数据库唯一索引/CAS 兜底；</li>
 *   <li>结果缓存默认 10 分钟，超过后同一个 Key 会被当成新请求。</li>
 * </ul>
 */
@Slf4j
public class IdempotencyService {

    /** 默认幂等窗口：10 分钟。与开发文档 §1.1 的约定一致。 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /** 处理中状态的固定前缀，后接本次请求的随机 token。 */
    private static final String PENDING_PREFIX = "P:";

    /** 已完成结果的前缀，后接业务返回值的 JSON。 */
    private static final String RESULT_PREFIX = "R:";

    /** scope 只允许这些字符，避免把 Key 层级打乱。 */
    private static final Pattern SCOPE_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    /** Idempotency-Key 允许字母、数字、下划线、点、冒号、短横线，长度不超过 128。 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,128}$");

    /**
     * 释放占位脚本：只有当前值仍等于自己的 token 时才删除。
     *
     * <p>为什么要用 Lua：如果业务执行超过 TTL，Key 可能已被另一个请求重新抢占；
     * 此时直接 DEL 会误删别人的占位，导致两个请求并发执行。比较后再删可以避免这个竞态。</p>
     */
    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 回写结果脚本：同样先比较 token，确认自己仍持有占位后再写入结果并设置 TTL。
     *
     * <p>参数：KEYS[1]=幂等 Key，ARGV[1]=token，ARGV[2]=结果内容，ARGV[3]=TTL 毫秒。</p>
     */
    private static final RedisScript<Long> CACHE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3]) return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    /**
     * @param redisTemplate Redis 客户端，由各服务的 spring-boot-starter-data-redis 提供
     * @param objectMapper  JSON 序列化器，Spring Boot 默认已注册
     */
    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate 不能为空");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 不能为空");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次幂等操作（默认 10 分钟窗口）。
     *
     * @param scope          业务范围，例如 {@code interview.result}，用于隔离不同接口的 Key
     * @param idempotencyKey 客户端传入的 Idempotency-Key
     * @param resultType     返回值的类型，用于把缓存 JSON 反序列化回来
     * @param action         真正的业务逻辑；只有抢到占位时才会执行
     * @param <T>            返回值类型
     * @return 本次执行结果，或同一 Key 之前缓存的结果
     */
    public <T> T execute(String scope, String idempotencyKey, Class<T> resultType, Supplier<T> action) {
        return execute(scope, idempotencyKey, resultType, DEFAULT_TTL, action);
    }

    /**
     * 执行一次幂等操作，自定义窗口。
     *
     * @param scope          业务范围
     * @param idempotencyKey 客户端传入的 Idempotency-Key
     * @param resultType     返回值类型
     * @param ttl            幂等窗口，必须大于 0
     * @param action         业务逻辑
     * @param <T>            返回值类型
     * @return 执行结果或缓存结果
     */
    public <T> T execute(String scope, String idempotencyKey, Class<T> resultType, Duration ttl, Supplier<T> action) {
        if (resultType == null) {
            throw new IllegalArgumentException("resultType 不能为空");
        }
        return execute(scope, idempotencyKey,
                objectMapper.getTypeFactory().constructType(resultType), ttl, action);
    }

    /**
     * 执行一次幂等操作，支持泛型返回值（默认 10 分钟窗口）。
     *
     * <p>示例：{@code execute("x", key, new TypeReference<PageResult<Item>>() {}, action)}</p>
     *
     * @param scope          业务范围
     * @param idempotencyKey 客户端传入的 Idempotency-Key
     * @param resultType     泛型类型引用
     * @param action         业务逻辑
     * @param <T>            返回值类型
     * @return 执行结果或缓存结果
     */
    public <T> T execute(String scope, String idempotencyKey, TypeReference<T> resultType, Supplier<T> action) {
        return execute(scope, idempotencyKey, resultType, DEFAULT_TTL, action);
    }

    /**
     * 执行一次幂等操作，支持泛型返回值与自定义窗口。
     *
     * @param scope          业务范围
     * @param idempotencyKey 客户端传入的 Idempotency-Key
     * @param resultType     泛型类型引用
     * @param ttl            幂等窗口，必须大于 0
     * @param action         业务逻辑
     * @param <T>            返回值类型
     * @return 执行结果或缓存结果
     */
    public <T> T execute(String scope, String idempotencyKey, TypeReference<T> resultType,
                         Duration ttl, Supplier<T> action) {
        if (resultType == null) {
            throw new IllegalArgumentException("resultType 不能为空");
        }
        JavaType javaType = objectMapper.getTypeFactory().constructType(resultType.getType());
        return execute(scope, idempotencyKey, javaType, ttl, action);
    }

    /**
     * 核心实现：占位 -> 执行 -> 缓存；或读取已有结果。
     *
     * @param scope          业务范围
     * @param idempotencyKey 客户端幂等键
     * @param resultType     反序列化目标类型
     * @param ttl            幂等窗口
     * @param action         业务逻辑
     * @param <T>            返回值类型
     * @return 执行结果或缓存结果
     */
    private <T> T execute(String scope, String idempotencyKey, JavaType resultType,
                          Duration ttl, Supplier<T> action) {
        String redisKey = buildKey(scope, idempotencyKey);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须大于 0");
        }
        if (action == null) {
            throw new IllegalArgumentException("action 不能为空");
        }

        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String token = PENDING_PREFIX + UUID.randomUUID();

        boolean acquired;
        try {
            // SET key token NX EX ttl：只有 Key 不存在时才能成功，这是幂等的核心。
            acquired = Boolean.TRUE.equals(ops.setIfAbsent(redisKey, token, ttl));
        } catch (RuntimeException ex) {
            // Redis 故障时降级：宁可承担重复风险，也不让所有写接口不可用。
            // 真正的数据安全仍由数据库唯一索引 / 状态 CAS 保证。
            log.warn("Redis 不可用，幂等降级为直接执行: key={}", redisKey, ex);
            return action.get();
        }

        if (!acquired) {
            return replay(redisKey, resultType);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException | Error ex) {
            // 业务失败：释放占位，允许客户端用同一个 Key 重试。
            release(redisKey, token);
            throw ex;
        }

        // 业务已成功。这里即使缓存失败也不能删占位：删了会让重试再次执行，
        // 而数据库可能已经写入，重复执行风险更高。保留占位让重试返回 409。
        cacheResult(redisKey, token, result, ttl);
        return result;
    }

    /**
     * 处理重复请求：读取占位里的内容。
     *
     * @param redisKey   Redis Key
     * @param resultType 反序列化目标类型
     * @param <T>        返回值类型
     * @return 已完成的结果
     * @throws BizException 仍在处理中（SYS409）或结果损坏（SYS500）时抛出
     */
    private <T> T replay(String redisKey, JavaType resultType) {
        String cached;
        try {
            cached = redisTemplate.opsForValue().get(redisKey);
        } catch (RuntimeException ex) {
            log.warn("读取幂等结果失败，按处理中返回: key={}", redisKey, ex);
            throw new BizException(ErrorCode.IDEMPOTENT_IN_PROGRESS);
        }

        if (cached == null || cached.startsWith(PENDING_PREFIX)) {
            // null：占位刚好过期；P:：第一次请求还没执行完。两种都让客户端稍后重试。
            throw new BizException(ErrorCode.IDEMPOTENT_IN_PROGRESS);
        }
        if (!cached.startsWith(RESULT_PREFIX)) {
            // 不是本服务写入的格式（例如 Key 冲突），保守地按处理中处理。
            log.warn("幂等 Key 内容格式未知: key={}", redisKey);
            throw new BizException(ErrorCode.IDEMPOTENT_IN_PROGRESS);
        }

        try {
            return objectMapper.readValue(cached.substring(RESULT_PREFIX.length()), resultType);
        } catch (JsonProcessingException ex) {
            log.error("幂等结果反序列化失败: key={}", redisKey, ex);
            throw new BizException(ErrorCode.SYS_ERROR, "幂等结果读取失败，请稍后重试", ex);
        }
    }

    /**
     * 回写执行结果。失败只记日志，不抛异常：
     * 业务已经成功，不能因为缓存问题让调用方以为失败而重试。
     *
     * @param redisKey Redis Key
     * @param token    本次占位 token
     * @param result   业务返回值
     * @param ttl      幂等窗口
     */
    private void cacheResult(String redisKey, String token, Object result, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(result);
            Long updated = redisTemplate.execute(
                    CACHE_SCRIPT,
                    Collections.singletonList(redisKey),
                    token,
                    RESULT_PREFIX + json,
                    String.valueOf(ttl.toMillis()));
            if (updated == null || updated == 0L) {
                log.warn("幂等占位已失效，结果未缓存: key={}", redisKey);
            }
        } catch (JsonProcessingException ex) {
            log.error("幂等结果序列化失败，保留占位: key={}", redisKey, ex);
        } catch (RuntimeException ex) {
            log.error("幂等结果写入 Redis 失败，保留占位: key={}", redisKey, ex);
        }
    }

    /**
     * 释放占位（仅当值仍等于自己的 token 时）。
     *
     * @param redisKey Redis Key
     * @param token    本次占位 token
     */
    private void release(String redisKey, String token) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(redisKey), token);
        } catch (RuntimeException ex) {
            // 释放失败不影响主流程：占位最多存活 10 分钟，之后自然过期。
            log.warn("释放幂等占位失败，等待 TTL 自然过期: key={}", redisKey, ex);
        }
    }

    /**
     * 校验并拼接 Redis Key。
     *
     * @param scope          业务范围
     * @param idempotencyKey 客户端幂等键
     * @return {@code idem:http:{scope}:{key}}
     * @throws BizException 参数缺失或格式非法时抛出 SYS400
     */
    private String buildKey(String scope, String idempotencyKey) {
        if (scope == null || !SCOPE_PATTERN.matcher(scope).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "幂等 scope 格式非法");
        }
        if (idempotencyKey == null || !KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Idempotency-Key 缺失或格式非法");
        }
        return RedisKeys.idempotentHttp(scope, idempotencyKey);
    }

    /**
     * 供调用方拼 Key 或排查问题时使用，实际执行时统一走上面的校验逻辑。
     *
     * @param scope          业务范围
     * @param idempotencyKey 客户端幂等键
     * @return {@code idem:http:{scope}:{key}}
     */
    public static String keyOf(String scope, String idempotencyKey) {
        return RedisKeys.idempotentHttp(scope, idempotencyKey);
    }
}
