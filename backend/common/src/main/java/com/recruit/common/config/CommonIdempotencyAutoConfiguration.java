package com.recruit.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.idempotency.IdempotencyService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 幂等服务的自动配置。
 *
 * <p>只有同时满足下面条件才注册：</p>
 * <ul>
 *   <li>类路径上有 Spring Data Redis 与 Jackson（common 中它们是 optional 依赖）；</li>
 *   <li>当前服务已经创建了 {@link StringRedisTemplate} 与 {@link ObjectMapper} Bean；</li>
 *   <li>业务服务没有自己声明 {@link IdempotencyService}（允许覆盖）。</li>
 * </ul>
 *
 * <p>notify/file 等没有 Redis 依赖的服务不会加载本配置，启动不受影响。</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
})
@ConditionalOnClass({StringRedisTemplate.class, ObjectMapper.class})
@ConditionalOnBean({StringRedisTemplate.class, ObjectMapper.class})
public class CommonIdempotencyAutoConfiguration {

    /**
     * 注册幂等服务。
     *
     * @param redisTemplate Redis 客户端
     * @param objectMapper  JSON 序列化器
     * @return 幂等服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotencyService idempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new IdempotencyService(redisTemplate, objectMapper);
    }
}
