package com.recruit.common.config;

import com.recruit.common.web.GlobalExceptionHandler;
import com.recruit.common.web.UserContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * common 的 Servlet 侧自动配置。
 *
 * <p>只在 Servlet Web 应用生效，网关（WebFlux）不会加载用户头过滤器；
 * 服务可通过声明同类型 Bean 覆盖默认实现。</p>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class CommonWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
