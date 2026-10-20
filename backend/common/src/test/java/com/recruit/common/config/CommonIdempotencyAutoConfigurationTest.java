package com.recruit.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link CommonIdempotencyAutoConfiguration} 的条件装配测试。
 *
 * <p>验证：Redis 与 Jackson 都存在时才创建 IdempotencyService；
 * 缺少 Redis 的服务（如 notify/file）不会创建，启动不受影响。</p>
 */
class CommonIdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonIdempotencyAutoConfiguration.class));

    /**
     * 两个依赖 Bean 都存在时，应注册一个 IdempotencyService。
     */
    @Test
    void createsServiceWhenDependenciesPresent() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withBean(ObjectMapper.class)
                .run(context -> assertThat(context).hasSingleBean(IdempotencyService.class));
    }

    /**
     * 类路径上没有 Spring Data Redis 时（如 notify/file），应跳过，不能因为 common 引入可选依赖而报错。
     */
    @Test
    void skipsWhenRedisClassMissing() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .withBean(ObjectMapper.class)
                .run(context -> assertThat(context).doesNotHaveBean(IdempotencyService.class));
    }

    /**
     * 没有 Redis 模板时跳过，避免没有 Redis 依赖的服务被动装配。
     */
    @Test
    void skipsWhenRedisTemplateMissing() {
        runner.withBean(ObjectMapper.class)
                .run(context -> assertThat(context).doesNotHaveBean(IdempotencyService.class));
    }
}
