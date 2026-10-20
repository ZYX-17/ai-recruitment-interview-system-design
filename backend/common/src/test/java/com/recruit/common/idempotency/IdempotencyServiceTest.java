package com.recruit.common.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IdempotencyService} 的单元测试。
 *
 * <p>用 Mockito 模拟 Redis，不依赖真实中间件。覆盖：首次执行并缓存、重复请求命中缓存、
 * 处理中返回 409、业务异常释放占位、Redis 故障降级、参数校验、泛型返回值。</p>
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String KEY = "idem:http:test.scope:k1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        // 部分用例（如参数校验）不会走到 Redis，用 lenient 避免 Mockito 报“多余打桩”。
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new IdempotencyService(redisTemplate, new ObjectMapper());
    }

    /**
     * 第一次请求：抢到占位后执行 action，返回业务结果。
     */
    @Test
    void executesActionWhenKeyIsNew() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();

        Sample result = service.execute("test.scope", "k1", Sample.class, () -> {
            calls.incrementAndGet();
            return new Sample("a", 1);
        });

        assertEquals(1, calls.get());
        assertEquals("a", result.id());
        assertEquals(1, result.count());
        verify(valueOps).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    /**
     * 重复请求：占位已存在，直接反序列化缓存结果，业务 action 不再执行。
     */
    @Test
    void returnsCachedResultForDuplicateRequest() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOps.get(KEY)).thenReturn("R:{\"id\":\"a\",\"count\":1}");
        AtomicBoolean executed = new AtomicBoolean(false);

        Sample result = service.execute("test.scope", "k1", Sample.class, () -> {
            executed.set(true);
            return new Sample("b", 2);
        });

        assertFalse(executed.get());
        assertEquals("a", result.id());
        assertEquals(1, result.count());
    }

    /**
     * 第一次请求还没结束时，重复请求应返回 409（SYS409），提示稍后重试。
     */
    @Test
    void rejectsDuplicateWhileFirstRequestIsRunning() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOps.get(KEY)).thenReturn("P:some-token");

        BizException ex = assertThrows(BizException.class,
                () -> service.execute("test.scope", "k1", Sample.class, () -> new Sample("a", 1)));

        assertEquals(ErrorCode.IDEMPOTENT_IN_PROGRESS, ex.getErrorCode());
    }

    /**
     * 业务抛异常时必须释放占位，否则用户修正参数后 10 分钟内都无法重试。
     */
    @Test
    void releasesKeyWhenActionFails() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.execute("test.scope", "k1", Sample.class, () -> {
                    throw new IllegalStateException("boom");
                }));

        // release 走 Lua 脚本：execute(script, keys, token)
        verify(redisTemplate).execute(any(), any(), anyString());
    }

    /**
     * Redis 不可用时降级为直接执行，保证写接口仍可用（幂等由数据库唯一约束兜底）。
     */
    @Test
    void degradesToDirectExecutionWhenRedisIsDown() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        Sample result = service.execute("test.scope", "k1", Sample.class, () -> new Sample("a", 1));

        assertEquals("a", result.id());
    }

    /**
     * scope 或 Idempotency-Key 非法时返回 SYS400，不触碰 Redis。
     */
    @Test
    void rejectsInvalidScopeAndKey() {
        BizException badScope = assertThrows(BizException.class,
                () -> service.execute("bad scope!", "k1", Sample.class, () -> new Sample("a", 1)));
        assertEquals(ErrorCode.PARAM_INVALID, badScope.getErrorCode());

        BizException badKey = assertThrows(BizException.class,
                () -> service.execute("test.scope", "bad key!", Sample.class, () -> new Sample("a", 1)));
        assertEquals(ErrorCode.PARAM_INVALID, badKey.getErrorCode());
    }

    /**
     * 泛型返回值走 TypeReference 重载，缓存 JSON 应能还原成 List。
     */
    @Test
    void supportsGenericResultType() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOps.get("idem:http:test.scope:k2")).thenReturn("R:[\"a\",\"b\"]");

        List<String> result = service.execute("test.scope", "k2",
                new TypeReference<List<String>>() { }, () -> List.of("x"));

        assertEquals(List.of("a", "b"), result);
    }

    /**
     * 缓存值格式未知（例如 Key 冲突）时，保守返回 409，避免误把脏数据当结果返回。
     */
    @Test
    void treatsUnknownContentAsInProgress() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOps.get(KEY)).thenReturn("garbage");

        BizException ex = assertThrows(BizException.class,
                () -> service.execute("test.scope", "k1", Sample.class, () -> new Sample("a", 1)));

        assertEquals(ErrorCode.IDEMPOTENT_IN_PROGRESS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("处理中"));
    }

    /** 测试用返回值，Jackson 可直接反序列化 record。 */
    private record Sample(String id, int count) {
    }
}
