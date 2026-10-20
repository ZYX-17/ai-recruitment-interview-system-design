package com.recruit.resume.config;

import com.recruit.common.trace.TraceIdUtil;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Feign 内部调用头：X-Internal-Token（file-service 的 /internal/** 鉴权）+ X-Trace-Id。
 *
 * <p>刻意不加 {@code @Configuration}：该类只通过 {@code @FeignClient(configuration=...)}
 * 引用，避免被组件扫描后变成全局拦截器。</p>
 */
public class InternalFeignConfig {

    @Bean
    public RequestInterceptor internalTokenInterceptor(
            @Value("${recruit.internal.token:dev-internal-token}") String token) {
        return template -> {
            if (StringUtils.hasText(token)) {
                template.header("X-Internal-Token", token);
            }
            String traceId = TraceIdUtil.get();
            if (StringUtils.hasText(traceId)) {
                template.header("X-Trace-Id", traceId);
            }
        };
    }
}
