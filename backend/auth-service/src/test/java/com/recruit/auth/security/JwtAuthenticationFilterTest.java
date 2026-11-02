package com.recruit.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.config.JwtProperties;
import com.recruit.auth.service.TokenRevocationService;
import com.recruit.auth.support.ApiErrorWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link JwtAuthenticationFilter} 的单元测试：不启动 Spring 容器，直接驱动过滤器。
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static KeyPair keys;

    @Mock
    private TokenRevocationService tokenRevocationService;

    private JwtTokenProvider provider;
    private JwtAuthenticationFilter filter;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("recruit-auth");
        properties.setKeyId("k1");
        properties.setAccessTokenTtlMinutes(120);
        properties.setRefreshTokenTtlDays(7);
        properties.setClockSkewSeconds(30);
        provider = new JwtTokenProvider(properties, keys.getPrivate(), Map.of("k1", keys.getPublic()));
        filter = new JwtAuthenticationFilter(provider, new ApiErrorWriter(new ObjectMapper()), tokenRevocationService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessTokenPopulatesSecurityContext() throws Exception {
        when(tokenRevocationService.isAccessTokenActive(any())).thenReturn(true);
        String token = provider.createAccessToken("10001", "hr01", List.of("HR_ADMIN"), 3L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        assertEquals("10001", principal.getUserId());
        assertEquals(3L, principal.getTokenVersion());
        assertTrue(principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_HR_ADMIN".equals(authority.getAuthority())));
        assertNotNull(chain.getRequest(), "认证通过后必须继续过滤器链");
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccess() throws Exception {
        String token = provider.createRefreshToken("10001", "hr01", List.of("HR_ADMIN"), 3L, "family-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
        assertNotNull(chain.getRequest(), "非法令牌也交给后续过滤器链判定");
    }

    @Test
    void malformedTokenLeavesContextEmptyButKeepsRequestAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer not-a-jwt");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    @Test
    void revokedTokenLeavesContextEmpty() throws Exception {
        when(tokenRevocationService.isAccessTokenActive(any())).thenReturn(false);
        String token = provider.createAccessToken("10001", "hr01", List.of("HR_ADMIN"), 3L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
    }

    @Test
    void missingHeaderPassesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/ping");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE));
        assertNotNull(chain.getRequest());
    }
}
