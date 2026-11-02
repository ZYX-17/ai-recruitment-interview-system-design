package com.recruit.auth.controller;

import com.recruit.auth.config.SecurityProperties;
import com.recruit.auth.dto.ChangePasswordForm;
import com.recruit.auth.dto.LoginForm;
import com.recruit.auth.dto.LoginResult;
import com.recruit.auth.dto.LogoutForm;
import com.recruit.auth.dto.RefreshForm;
import com.recruit.auth.dto.RefreshResult;
import com.recruit.auth.dto.UserSummary;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.security.TokenType;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.service.AuthService;
import com.recruit.auth.service.TokenService;
import com.recruit.auth.support.RefreshCookieFactory;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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

    @Test
    void refreshReadsCookieAndRotatesIt() {
        when(authService.refresh(eq("old-cookie"), any())).thenReturn(refreshResult());
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<RefreshResult> body = controller.refresh(null, requestWithCookie("old-cookie"), response);

        assertNull(body.getData().refreshToken());
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("recruit_rt=new-refresh"));
        verify(authService).refresh(eq("old-cookie"), any());
    }

    @Test
    void refreshWithoutBodyDoesNotFailWithUnsupportedMediaType() throws Exception {
        when(authService.refresh(isNull(), any())).thenReturn(refreshResult());
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isOk());
    }

    @Test
    void localModeAcceptsJsonBody() throws Exception {
        properties.getRefreshCookie().setExposeInBody(true);
        when(authService.refresh(eq("body-token"), any())).thenReturn(refreshResult());
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"body-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refreshFailureClearsCookie() {
        when(authService.refresh(eq("bad-cookie"), any()))
                .thenThrow(new BizException(ErrorCode.AUTH_REFRESH_REPLAYED));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(BizException.class,
                () -> controller.refresh(null, requestWithCookie("bad-cookie"), response));

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    void bodyTokenIsIgnoredOutsideLocalMode() {
        when(authService.refresh(isNull(), any())).thenReturn(refreshResult());
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);

        controller.refresh(refreshForm("body-token"), new MockHttpServletRequest(), new MockHttpServletResponse());

        verify(authService).refresh(isNull(), any());
    }

    @Test
    void localModeAcceptsBodyToken() {
        properties.getRefreshCookie().setExposeInBody(true);
        when(authService.refresh(eq("body-token"), any())).thenReturn(refreshResult());
        when(tokenService.refreshTtlSeconds()).thenReturn(604800L);

        controller.refresh(refreshForm("body-token"), new MockHttpServletRequest(), new MockHttpServletResponse());

        verify(authService).refresh(eq("body-token"), any());
    }

    @Test
    void logoutClearsRefreshCookieAndPassesAllDevicesFlag() {
        LogoutForm form = new LogoutForm();
        form.setAllDevices(Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserPrincipal principal = principal();

        Result<Void> body = controller.logout(form, principal, new MockHttpServletRequest(), response);

        assertTrue(body.isSuccess());
        verify(authService).logout(eq(principal), isNull(), eq(true), any());
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    void logoutWithoutBodyDefaultsToCurrentDevice() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserPrincipal principal = principal();

        controller.logout(null, principal, requestWithCookie("refresh-token"), response);

        verify(authService).logout(eq(principal), eq("refresh-token"), eq(false), any());
        assertNotNull(response.getHeader(HttpHeaders.SET_COOKIE));
    }

    @Test
    void changePasswordClearsRefreshCookie() {
        ChangePasswordForm form = new ChangePasswordForm();
        form.setOldPassword("old-secret");
        form.setNewPassword("new-secret-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/auth/password");

        Result<Void> body = controller.changePassword(form, principal(), request, response);

        assertTrue(body.isSuccess());
        verify(authService).changePassword(10001L, form, request);
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    void changePasswordWithoutPrincipalReturnsUnauthenticated() {
        ChangePasswordForm form = new ChangePasswordForm();
        form.setOldPassword("old-secret");
        form.setNewPassword("new-secret-123");

        BizException exception = assertThrows(BizException.class,
                () -> controller.changePassword(form, null, new MockHttpServletRequest(), new MockHttpServletResponse()));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.GW_UNAUTHENTICATED, exception.getErrorCode());
        verify(authService, org.mockito.Mockito.never()).changePassword(org.mockito.ArgumentMatchers.anyLong(),
                any(), any());
    }

    private static UserPrincipal principal() {
        Instant now = Instant.now();
        return UserPrincipal.from(new JwtClaims("10001", "hr01", List.of("RECRUITER"), 3L, "jti-1",
                TokenType.ACCESS, null, now, now.plusSeconds(600)));
    }

    private static LoginResult result(String refreshToken) {
        UserSummary user = new UserSummary("10001", "hr01", "招聘专员", "EMPLOYEE", null, null, null);
        return new LoginResult("access-token", "Bearer", 7200, refreshToken, user,
                List.of("RECRUITER"), List.of("interview:job:view"), 3);
    }

    private static RefreshResult refreshResult() {
        return new RefreshResult("new-access", "Bearer", 7200, "new-refresh");
    }

    private static RefreshForm refreshForm(String token) {
        RefreshForm form = new RefreshForm();
        form.setRefreshToken(token);
        return form;
    }

    private static MockHttpServletRequest requestWithCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(new Cookie("recruit_rt", token));
        return request;
    }

    private static LoginForm form() {
        LoginForm form = new LoginForm();
        form.setUsername("hr01");
        form.setPassword("secret");
        return form;
    }
}
