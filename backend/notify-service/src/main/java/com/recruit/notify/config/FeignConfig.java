package com.recruit.notify.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Feign 公共配置：服务间调用统一带 {@code X-Internal-Token}。
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor internalTokenInterceptor(NotifySecurityProperties properties) {
        return template -> {
            if (StringUtils.hasText(properties.getInternalToken())) {
                template.header("X-Internal-Token", properties.getInternalToken());
            }
        };
    }
}
