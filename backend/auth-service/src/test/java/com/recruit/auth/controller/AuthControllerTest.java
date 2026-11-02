package com.recruit.auth.controller;

import com.recruit.auth.config.SecurityProperties;
import com.recruit.auth.dto.LoginForm;
import com.recruit.auth.dto.LoginResult;
import com.recruit.auth.dto.UserSummary;
import com.recruit.auth.service.AuthService;
import com.recruit.auth.service.TokenService;
import com.recruit.auth.support.RefreshCookieFactory;
import com.recruit.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link AuthController} 的 Cookie/响应体口径测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private TokenService tokenService;

    private SecurityProperties properties;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        controller = new AuthController(authService, new RefreshCookieFactory(properties), properties, tokenService);
    }

    @Test
    void refreshTokenGoesToCookieButNotBodyByDefault() {
        when(authService.login(any(), any())).thenReturn(result("refresh-token"));
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<LoginResult> body = controller.login(form(), new MockHttpServletRequest(), response);

        assertNull(body.getData().refreshToken());
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("recruit_rt=refresh-token"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Path=/api/auth"));
        assertTrue(setCookie.contains("SameSite=Strict"));
    }

    @Test
    void localProfileCanExposeRefreshTokenInBody() {
        properties.getRefreshCookie().setExposeInBody(true);
        when(authService.login(any(), any())).thenReturn(result("refresh-token"));
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);

        Result<LoginResult> body = controller.login(form(), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertNotNull(body.getData().refreshToken());
    }

    private static LoginResult result(String refreshToken) {
        UserSummary user = new UserSummary("10001", "hr01", "招聘专员", "EMPLOYEE", null, null, null);
        return new LoginResult("access-token", "Bearer", 7200, refreshToken, user,
                List.of("RECRUITER"), List.of("interview:job:view"), 3);
    }

    private static LoginForm form() {
        LoginForm form = new LoginForm();
        form.setUsername("hr01");
        form.setPassword("secret");
        return form;
    }
}
