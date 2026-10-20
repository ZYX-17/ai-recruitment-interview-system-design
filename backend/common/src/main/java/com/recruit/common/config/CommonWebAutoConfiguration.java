package com.recruit.common.config;

import com.recruit.common.web.GlobalExceptionHandler;
import com.recruit.common.web.UserContextFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * common 模块的 Servlet 侧自动配置。
 *
 * <p>为什么需要它：各业务服务（auth/interview/notify/file）都依赖 common，
 * 但不应在每个服务里重复写 {@code @Bean UserContextFilter} 和 {@code @Bean GlobalExceptionHandler}。
 * Spring Boot 的自动配置机制会在启动时读取
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}，
 * 发现本类后自动注册下面的 Bean。业务服务只要引入 common 依赖就生效，零配置。</p>
 *
 * <p>三个条件注解的含义：</p>
 * <ul>
 *   <li>{@code @ConditionalOnWebApplication(SERVLET)}：只有 Servlet Web 应用才注册。
 *       网关基于 WebFlux，不会加载 Servlet 过滤器，避免无意义甚至报错的 Bean。</li>
 *   <li>{@code @ConditionalOnClass("jakarta.servlet.Filter")}：类路径上确实有 Servlet API 时才注册。
 *       纯 Job/单元测试环境没有 Servlet 依赖，跳过即可。</li>
 *   <li>{@code @ConditionalOnMissingBean}：业务服务如果自己声明了同类型 Bean，以业务服务的为准，
 *       方便个别服务定制（例如测试环境想换一个异常处理器）。</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
public class CommonWebAutoConfiguration {

    /**
     * 注册用户上下文过滤器。
     *
     * <p>过滤器本身通过 {@code @Order(HIGHEST_PRECEDENCE + 20)} 控制执行顺序，
     * 这里只负责创建 Bean。</p>
     *
     * @return 用户上下文过滤器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public UserContextFilter userContextFilter() {
        return new UserContextFilter();
    }

    /**
     * 注册全局异常处理器。
     *
     * <p>它带 {@code @RestControllerAdvice}，被注册成 Bean 后会自动被 Spring MVC 扫描为
     * Controller 增强，无需额外注解。</p>
     *
     * @return 全局异常处理器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
