package com.recruit.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.config.JwtProperties;
import com.recruit.auth.config.SecurityConfig;
import com.recruit.auth.service.PermissionService;
import com.recruit.auth.service.TokenRevocationService;
import com.recruit.auth.support.ApiErrorWriter;
import com.recruit.auth.support.PermissionChecker;
import com.recruit.auth.support.SecurityExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-7 鉴权矩阵：真实 {@link SecurityFilterChain} + 方法级 {@code @PreAuthorize}。
 *
 * <p>覆盖 docs/4 §10.2 中 auth-service 可以端到端验证的部分：</p>
 * <ul>
 *   <li>S1 未登录访问：401 {@code GW9001}，白名单端点免登录；</li>
 *   <li>S2 越权 API：已认证但无权限，403 {@code AUTH1101}（方法级出口）；</li>
 *   <li>S6 禁用/改密：token_version/黑名单判定失败后旧 Access 立即 401；</li>
 *   <li>S8 内部接口：已认证访问 {@code /internal/**} 仍是 403 {@code GW9005}。</li>
 * </ul>
 *
 * <p>S5 Refresh 重放由 {@code RefreshSessionServiceTest#replayedTokenRevokesWholeFamily} 覆盖；
 * S3 候选人水平越权与 S4 面试官未分配场次属于 interview-service（P4/P7），
 * 待投递/评价/Offer/排期接口落地后补，不在本测试假装通过。</p>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = AuthzMatrixTest.TestConfig.class)
class AuthzMatrixTest {

    private static final long USER_ID = 10001L;
    private static final KeyPair KEYS = generateKeys();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private TokenRevocationService tokenRevocationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Mockito.reset(permissionService, tokenRevocationService);
        when(permissionService.permissionCodes(USER_ID)).thenReturn(List.of());
        when(tokenRevocationService.isAccessTokenActive(any())).thenReturn(true);
        FilterChainProxy securityChain = context.getBean("springSecurityFilterChain", FilterChainProxy.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(securityChain, "/*")
                .build();
    }

    @Test
    void s1AnonymousRequestToProtectedApiReturns401Gw9001() throws Exception {
        mockMvc.perform(get("/probe/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GW9001"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void s1WhitelistedEndpointIsReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void s2AuthenticatedWithoutPermissionReturns403Auth1101() throws Exception {
        mockMvc.perform(get("/probe/admin").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH1101"));
    }

    @Test
    void s2AuthenticatedWithPermissionIsAllowed() throws Exception {
        when(permissionService.permissionCodes(USER_ID)).thenReturn(List.of("probe:admin"));

        mockMvc.perform(get("/probe/admin").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void s6RevokedOrVersionMismatchedAccessTokenReturns401() throws Exception {
        when(tokenRevocationService.isAccessTokenActive(any())).thenReturn(false);

        mockMvc.perform(get("/probe/protected").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GW9001"));
    }

    @Test
    void s8InternalPathStaysForbiddenEvenWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/internal/notify/send").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GW9005"));
    }

    private String bearer() {
        return "Bearer " + tokenProvider.createAccessToken(
                String.valueOf(USER_ID), "hr01", List.of("RECRUITER"), 3L);
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("生成测试密钥失败", e);
        }
    }

    /**
     * 最小运行环境：复用生产 {@link SecurityConfig} 的过滤器链，只把外部依赖换成 Mock。
     */
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ApiErrorWriter apiErrorWriter(ObjectMapper objectMapper) {
            return new ApiErrorWriter(objectMapper);
        }

        @Bean
        RestAuthenticationEntryPoint restAuthenticationEntryPoint(ApiErrorWriter apiErrorWriter) {
            return new RestAuthenticationEntryPoint(apiErrorWriter);
        }

        @Bean
        RestAccessDeniedHandler restAccessDeniedHandler(ApiErrorWriter apiErrorWriter) {
            return new RestAccessDeniedHandler(apiErrorWriter);
        }

        @Bean
        PermissionService permissionService() {
            return Mockito.mock(PermissionService.class);
        }

        @Bean
        PermissionChecker permissionChecker(PermissionService permissionService) {
            return new PermissionChecker(permissionService);
        }

        @Bean
        TokenRevocationService tokenRevocationService() {
            return Mockito.mock(TokenRevocationService.class);
        }

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setIssuer("recruit-auth");
            properties.setKeyId("k1");
            properties.setAccessTokenTtlMinutes(120);
            properties.setRefreshTokenTtlDays(7);
            properties.setClockSkewSeconds(30);
            return properties;
        }

        @Bean
        JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
            return new JwtTokenProvider(properties, KEYS.getPrivate(), Map.of("k1", KEYS.getPublic()));
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                                         ApiErrorWriter apiErrorWriter,
                                                         TokenRevocationService tokenRevocationService) {
            return new JwtAuthenticationFilter(tokenProvider, apiErrorWriter, tokenRevocationService);
        }

        @Bean
        SecurityExceptionAdvice securityExceptionAdvice() {
            return new SecurityExceptionAdvice();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                JwtAuthenticationFilter jwtAuthenticationFilter,
                                                RestAuthenticationEntryPoint entryPoint,
                                                RestAccessDeniedHandler accessDeniedHandler) throws Exception {
            return new SecurityConfig(jwtAuthenticationFilter, entryPoint, accessDeniedHandler)
                    .securityFilterChain(http);
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    /**
     * 探针端点：只验证安全链行为，不依赖任何业务表。
     */
    @RestController
    static class ProbeController {

        @GetMapping("/api/auth/ping")
        public String ping() {
            return "pong";
        }

        @GetMapping("/probe/protected")
        public String protectedEndpoint() {
            return "protected";
        }

        @GetMapping("/probe/admin")
        @PreAuthorize("@permissionChecker.has('probe:admin')")
        public String adminEndpoint() {
            return "admin";
        }
    }
}
