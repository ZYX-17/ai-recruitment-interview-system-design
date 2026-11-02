package com.recruit.auth.config;

import com.recruit.auth.security.JwtAuthenticationFilter;
import com.recruit.auth.security.RestAccessDeniedHandler;
import com.recruit.auth.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * auth-service 安全配置：无状态 JWT + 统一 JSON 错误。
 *
 * <p><b>边界</b>：auth-service 自己签发的 Access Token 由本服务过滤器校验；
 * 业务服务的令牌校验在网关（P3-1）完成，业务服务只信任网关注入的可信头。</p>
 *
 * <p><b>白名单</b>只放“拿令牌之前必须能访问”的接口和健康/文档端点；
 * {@code /internal/**} 显式拒绝，避免服务间接口被外部直接调用。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, SecurityProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    /** 无需登录即可访问的端点。 */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/ping",
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * 过滤器链。
     *
     * <p>关闭 CSRF：令牌走 Authorization 头 / HttpOnly Cookie，不使用浏览器表单会话；
     * Refresh Cookie 的 SameSite=Strict 与 Path=/api/auth 在 P1-4 写入时设置。</p>
     *
     * @param http Spring Security 构建器
     * @return 过滤器链
     * @throws Exception 构建失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/internal/**").denyAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 关掉容器级自动注册。
     *
     * <p>Spring Boot 会把所有 {@link jakarta.servlet.Filter} Bean 注册成 Servlet 过滤器，
     * 而本过滤器已经加在 Security 链上。不禁用的话会先在容器链执行一次，
     * 随后被 Security 的 SecurityContextHolderFilter 覆盖，导致认证丢失。</p>
     *
     * @param filter JWT 过滤器
     * @return 禁用状态的注册信息
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 密码哈希：BCrypt（P1-3 登录/改密使用）。
     *
     * @return BCrypt 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
