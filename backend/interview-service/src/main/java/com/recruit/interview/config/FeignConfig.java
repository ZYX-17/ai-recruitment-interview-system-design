package com.recruit.interview.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Feign 公共配置：服务间调用统一带 {@code X-Internal-Token}。
 *
 * <p>令牌由环境变量注入；为空时不加头，下游会按 403 fail-closed 拒绝。</p>
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalTokenInterceptor(InterviewSecurityProperties properties) {
        return template -> {
            if (StringUtils.hasText(properties.getInternalToken())) {
                template.header("X-Internal-Token", properties.getInternalToken());
            }
        };
    }
}
