package com.recruit.interview.config;

import com.recruit.interview.security.GatewayHeaderAuthenticationFilter;
import com.recruit.interview.security.InternalTokenFilter;
import com.recruit.interview.security.RestAccessDeniedHandler;
import com.recruit.interview.security.RestAuthenticationEntryPoint;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * interview-service 安全链：只信任网关注入的 {@code X-User-*}（docs/4 §2.2/§3.3.2）。
 *
 * <p>业务服务不重复验 JWT；权限码由 {@code @PreAuthorize("@permissionChecker.has(...)")}
 * 经 auth-service 内部接口解析。门户（token 自助）与健康检查放行。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(InterviewSecurityProperties.class)
@RequiredArgsConstructor
public class InterviewSecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/interview/ping",
            "/api/interview/portal/**",
            "/actuator/health",
            "/actuator/health/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    private final GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter;
    private final InternalTokenFilter internalTokenFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // 内部接口由 InternalTokenFilter 校验 X-Internal-Token（网关已对公网 403）
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(gatewayHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalTokenFilter, GatewayHeaderAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 两个自定义过滤器只挂在 Security 链上，禁止容器再注册一次。
     *
     * @param filter 网关头认证过滤器
     * @return 禁用状态的注册信息
     */
    @Bean
    public FilterRegistrationBean<GatewayHeaderAuthenticationFilter> gatewayHeaderFilterRegistration(
            GatewayHeaderAuthenticationFilter filter) {
        FilterRegistrationBean<GatewayHeaderAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 内部令牌过滤器容器级注册开关。
     *
     * @param filter 内部令牌过滤器
     * @return 禁用状态的注册信息
     */
    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilterRegistration(
            InternalTokenFilter filter) {
        FilterRegistrationBean<InternalTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
