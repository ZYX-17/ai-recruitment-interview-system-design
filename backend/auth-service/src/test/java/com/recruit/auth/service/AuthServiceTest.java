package com.recruit.auth.service;

import com.recruit.auth.dto.ChangePasswordForm;
import com.recruit.auth.dto.LoginForm;
import com.recruit.auth.dto.LoginResult;
import com.recruit.auth.dto.RefreshResult;
import com.recruit.auth.entity.IamDepartment;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.security.TokenType;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService} 登录口径的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private IamUserMapper userMapper;
    @Mock
    private IamDepartmentMapper departmentMapper;
    @Mock
    private PermissionService permissionService;
    @Mock
    private TokenService tokenService;
    @Mock
    private RefreshSessionService refreshSessionService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuditService auditService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private TokenRevocationService tokenRevocationService;
    @Mock
    private MustChangePasswordService mustChangePasswordService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, departmentMapper, permissionService, tokenService,
                refreshSessionService, loginAttemptService, auditService, passwordEncoder, redisTemplate,
                tokenRevocationService, mustChangePasswordService);
    }

    @Test
    void successfulLoginIssuesTokensAndFillsRedisTokenVersion() {
        IamUser user = activeUser("secret");
        user.setDeptId(20001L);
        when(loginAttemptService.isLocked("hr01")).thenReturn(false);
        when(userMapper.selectByUsername("hr01")).thenReturn(user);
        when(permissionService.roleCodes(10001L)).thenReturn(List.of("RECRUITER"));
        when(permissionService.permissionCodes(10001L)).thenReturn(List.of("interview:job:view"));
        when(tokenService.createAccessToken(user, List.of("RECRUITER"), 3L)).thenReturn("access-token");
        when(tokenService.accessTtlSeconds()).thenReturn(7200L);
        when(refreshSessionService.issue(user, List.of("RECRUITER"), 3L, "10.0.0.1", "JUnit"))
                .thenReturn(new RefreshSessionService.IssuedRefresh("refresh-token", "family-1", "jti-1"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        IamDepartment department = new IamDepartment();
        department.setId(20001L);
        department.setDeptName("招聘部");
        department.setStatus("ACTIVE");
        when(departmentMapper.selectById(20001L)).thenReturn(department);

        LoginResult result = authService.login(form("hr01", "secret", null), request("10.0.0.1", "JUnit"));

        assertEquals("access-token", result.accessToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(7200L, result.expiresIn());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(List.of("RECRUITER"), result.roles());
        assertEquals(List.of("interview:job:view"), result.permissions());
        assertEquals(3L, result.tokenVersion());
        assertEquals("招聘部", result.user().deptName());
        verify(valueOperations).set("auth:tv:10001", "3");
        verify(loginAttemptService).reset("hr01");
        verify(auditService).recordQuietly(eq(10001L), eq("LOGIN"), eq("USER"), eq("10001"),
                eq(true), eq("10.0.0.1"), anyMap());
    }

    @Test
    void wrongPasswordCountsFailureAndReturnsAuth1001() {
        IamUser user = activeUser("secret");
        when(loginAttemptService.isLocked("hr01")).thenReturn(false);
        when(userMapper.selectByUsername("hr01")).thenReturn(user);
        when(loginAttemptService.recordFailure("hr01")).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(form("hr01", "wrong", null), request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_LOGIN_FAILED, exception.getErrorCode());
        verify(loginAttemptService).recordFailure("hr01");
        verify(loginAttemptService, never()).reset("hr01");
        verify(auditService).recordQuietly(eq(10001L), eq("LOGIN"), eq("USER"), eq("10001"),
                eq(false), eq("10.0.0.1"), argThat(map -> "PASSWORD_MISMATCH".equals(map.get("reason"))));
        verifyNoInteractions(tokenService, refreshSessionService);
    }

    @Test
    void lockedAccountFailsWithoutQueryingUser() {
        when(loginAttemptService.isLocked("hr01")).thenReturn(true);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(form("hr01", "secret", null), request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_LOGIN_FAILED, exception.getErrorCode());
        verifyNoInteractions(userMapper, tokenService, refreshSessionService);
        verify(auditService).recordQuietly(isNull(), eq("LOGIN"), eq("USER"), isNull(),
                eq(false), eq("10.0.0.1"), argThat(map -> "LOCKED".equals(map.get("reason"))));
    }

    @Test
    void unknownUserStillCountsFailureWithoutLeakingExistence() {
        when(loginAttemptService.isLocked("ghost")).thenReturn(false);
        when(userMapper.selectByUsername("ghost")).thenReturn(null);
        when(loginAttemptService.recordFailure("ghost")).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(form("ghost", "secret", null), request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_LOGIN_FAILED, exception.getErrorCode());
        verify(loginAttemptService).recordFailure("ghost");
        verify(auditService).recordQuietly(isNull(), eq("LOGIN"), eq("USER"), isNull(),
                eq(false), eq("10.0.0.1"), argThat(map -> "USER_NOT_FOUND".equals(map.get("reason"))));
        verifyNoInteractions(tokenService, refreshSessionService);
    }

    @Test
    void disabledAccountReturnsAuth1101WithoutIssuingToken() {
        IamUser user = activeUser("secret");
        user.setStatus("DISABLED");
        when(loginAttemptService.isLocked("hr01")).thenReturn(false);
        when(userMapper.selectByUsername("hr01")).thenReturn(user);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(form("hr01", "secret", null), request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_FORBIDDEN, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("禁用"));
        verify(loginAttemptService, never()).recordFailure("hr01");
        verifyNoInteractions(tokenService, refreshSessionService);
    }

    @Test
    void userTypeMismatchIsTreatedAsCredentialFailure() {
        IamUser user = activeUser("secret");
        when(loginAttemptService.isLocked("hr01")).thenReturn(false);
        when(userMapper.selectByUsername("hr01")).thenReturn(user);
        when(loginAttemptService.recordFailure("hr01")).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(form("hr01", "secret", "CANDIDATE"), request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_LOGIN_FAILED, exception.getErrorCode());
        verify(auditService).recordQuietly(eq(10001L), eq("LOGIN"), eq("USER"), eq("10001"),
                eq(false), eq("10.0.0.1"), argThat(map -> "USER_TYPE_MISMATCH".equals(map.get("reason"))));
    }

    @Test
    void candidateWithoutPasswordCannotLogin() {
        IamUser user = activeUser(null);
        when(loginAttemptService.isLocked("hr01")).thenReturn(false);
        when(userMapper.selectByUsername("hr01")).thenReturn(user);
        when(loginAttemptService.recordFailure("hr01")).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> authService.login(form("hr01", "secret", null), request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_LOGIN_FAILED, exception.getErrorCode());
        verifyNoInteractions(tokenService, refreshSessionService);
    }

    @Test
    void missingDepartmentLeavesDeptFieldsNull() {
        IamUser user = activeUser("secret");
        when(loginAttemptService.isLocked("hr01")).thenReturn(false);
        when(userMapper.selectByUsername("hr01")).thenReturn(user);
        when(permissionService.roleCodes(10001L)).thenReturn(List.of("RECRUITER"));
        when(permissionService.permissionCodes(10001L)).thenReturn(List.of());
        when(tokenService.createAccessToken(user, List.of("RECRUITER"), 3L)).thenReturn("access-token");
        when(tokenService.accessTtlSeconds()).thenReturn(7200L);
        when(refreshSessionService.issue(user, List.of("RECRUITER"), 3L, "10.0.0.1", "JUnit"))
                .thenReturn(new RefreshSessionService.IssuedRefresh("refresh-token", "family-1", "jti-1"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LoginResult result = authService.login(form("hr01", "secret", null), request("10.0.0.1", "JUnit"));

        assertNull(result.user().deptId());
        assertNull(result.user().deptName());
    }

    @Test
    void refreshRejectsMalformedToken() {
        when(tokenService.parseRefreshToken("bad-token")).thenThrow(new IllegalArgumentException("bad"));

        BizException exception = assertThrows(BizException.class,
                () -> authService.refresh("bad-token", request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
        verifyNoInteractions(refreshSessionService);
    }

    @Test
    void refreshRejectsMissingToken() {
        BizException exception = assertThrows(BizException.class,
                () -> authService.refresh("  ", request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
    }

    @Test
    void refreshRejectsUnknownUser() {
        when(tokenService.parseRefreshToken("refresh-token")).thenReturn(refreshClaims());
        when(userMapper.selectById(10001L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> authService.refresh("refresh-token", request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
        verifyNoInteractions(refreshSessionService);
    }

    @Test
    void refreshRotatesAndIssuesNewAccessToken() {
        IamUser user = activeUser("secret");
        JwtClaims claims = refreshClaims();
        when(tokenService.parseRefreshToken("refresh-token")).thenReturn(claims);
        when(userMapper.selectById(10001L)).thenReturn(user);
        when(permissionService.roleCodes(10001L)).thenReturn(List.of("RECRUITER"));
        when(refreshSessionService.rotate("refresh-token", claims, user, List.of("RECRUITER"), 3L,
                "10.0.0.1", "JUnit"))
                .thenReturn(new RefreshSessionService.IssuedRefresh("new-refresh", "family-1", "jti-new"));
        when(tokenService.createAccessToken(user, List.of("RECRUITER"), 3L)).thenReturn("new-access");
        when(tokenService.accessTtlSeconds()).thenReturn(7200L);

        RefreshResult result = authService.refresh("refresh-token", request("10.0.0.1", "JUnit"));

        assertEquals("new-access", result.accessToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(7200L, result.expiresIn());
        assertEquals("new-refresh", result.refreshToken());
    }

    @Test
    void logoutCurrentDeviceBlacklistsAccessAndRevokesRefreshFamily() {
        UserPrincipal principal = principal("10001", "jti-1", 3L);

        authService.logout(principal, "refresh-token", false, request("10.0.0.1", "JUnit"));

        verify(tokenRevocationService).blacklist(eq("jti-1"), any());
        verify(refreshSessionService).revoke("refresh-token");
        verify(tokenRevocationService, never()).revokeAllSessions(10001L);
        verify(auditService).recordQuietly(eq(10001L), eq("LOGOUT"), eq("USER"), eq("10001"),
                eq(true), eq("10.0.0.1"), argThat(map -> Boolean.FALSE.equals(map.get("allDevices"))));
    }

    @Test
    void logoutAllDevicesBumpsTokenVersionAndSkipsSingleFamilyRevoke() {
        UserPrincipal principal = principal("10001", "jti-1", 3L);

        authService.logout(principal, "refresh-token", true, request("10.0.0.1", "JUnit"));

        verify(tokenRevocationService).revokeAllSessions(10001L);
        verify(refreshSessionService, never()).revoke(anyString());
        verify(auditService).recordQuietly(eq(10001L), eq("LOGOUT"), eq("USER"), eq("10001"),
                eq(true), eq("10.0.0.1"), argThat(map -> Boolean.TRUE.equals(map.get("allDevices"))));
    }

    @Test
    void logoutWithoutPrincipalOrCookieIsIdempotent() {
        authService.logout(null, null, false, request("10.0.0.1", "JUnit"));

        verifyNoInteractions(tokenRevocationService, refreshSessionService);
        verify(auditService).recordQuietly(isNull(), eq("LOGOUT"), eq("USER"), isNull(),
                eq(true), eq("10.0.0.1"), anyMap());
    }

    @Test
    void logoutAllDevicesFallsBackToRefreshTokenUser() {
        when(tokenService.parseRefreshToken("refresh-token")).thenReturn(refreshClaims());

        authService.logout(null, "refresh-token", true, request("10.0.0.1", "JUnit"));

        verify(tokenRevocationService).revokeAllSessions(10001L);
        verify(refreshSessionService, never()).revoke(anyString());
    }

    @Test
    void changePasswordUpdatesHashAndRevokesAllSessions() {
        IamUser user = activeUser("old-secret");
        when(userMapper.selectById(10001L)).thenReturn(user);
        when(userMapper.updatePasswordHash(eq(10001L), anyString())).thenReturn(1);

        authService.changePassword(10001L, passwordForm("old-secret", "new-secret-123"),
                request("10.0.0.1", "JUnit"));

        verify(userMapper).updatePasswordHash(eq(10001L), argThat(hash -> passwordEncoder.matches("new-secret-123", hash)));
        verify(tokenRevocationService).revokeAllSessions(10001L);
        verify(auditService).recordQuietly(eq(10001L), eq("PASSWORD_CHANGE"), eq("USER"), eq("10001"),
                eq(true), eq("10.0.0.1"), argThat(map -> "OK".equals(map.get("reason"))));
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        IamUser user = activeUser("old-secret");
        when(userMapper.selectById(10001L)).thenReturn(user);

        BizException exception = assertThrows(BizException.class,
                () -> authService.changePassword(10001L, passwordForm("wrong", "new-secret-123"),
                        request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_PASSWORD_MISMATCH, exception.getErrorCode());
        verify(userMapper, never()).updatePasswordHash(any(), anyString());
        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    void changePasswordRejectsSameAsOld() {
        IamUser user = activeUser("old-secret");
        when(userMapper.selectById(10001L)).thenReturn(user);

        BizException exception = assertThrows(BizException.class,
                () -> authService.changePassword(10001L, passwordForm("old-secret", "old-secret"),
                        request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.PARAM_INVALID, exception.getErrorCode());
        verify(userMapper, never()).updatePasswordHash(any(), anyString());
        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    void changePasswordRejectsDisabledUser() {
        IamUser user = activeUser("old-secret");
        user.setStatus("DISABLED");
        when(userMapper.selectById(10001L)).thenReturn(user);

        BizException exception = assertThrows(BizException.class,
                () -> authService.changePassword(10001L, passwordForm("old-secret", "new-secret-123"),
                        request("10.0.0.1", "JUnit")));

        assertEquals(ErrorCode.AUTH_FORBIDDEN, exception.getErrorCode());
        verify(userMapper, never()).updatePasswordHash(any(), anyString());
        verifyNoInteractions(tokenRevocationService);
    }

    private IamUser activeUser(String rawPassword) {
        IamUser user = new IamUser();
        user.setId(10001L);
        user.setUsername("hr01");
        user.setDisplayName("招聘专员");
        user.setUserType("EMPLOYEE");
        user.setStatus("ACTIVE");
        user.setTokenVersion(3);
        user.setPasswordHash(rawPassword == null ? null : passwordEncoder.encode(rawPassword));
        return user;
    }

    private static JwtClaims refreshClaims() {
        Instant now = Instant.now();
        return new JwtClaims("10001", "hr01", List.of("RECRUITER"), 3L, "jti-old",
                TokenType.REFRESH, "family-1", now, now.plusSeconds(604800));
    }

    private static UserPrincipal principal(String userId, String jti, long tokenVersion) {
        Instant now = Instant.now();
        return UserPrincipal.from(new JwtClaims(userId, "hr01", List.of("RECRUITER"), tokenVersion, jti,
                TokenType.ACCESS, null, now, now.plusSeconds(600)));
    }

    private static ChangePasswordForm passwordForm(String oldPassword, String newPassword) {
        ChangePasswordForm form = new ChangePasswordForm();
        form.setOldPassword(oldPassword);
        form.setNewPassword(newPassword);
        return form;
    }

    private static LoginForm form(String username, String password, String userType) {
        LoginForm form = new LoginForm();
        form.setUsername(username);
        form.setPassword(password);
        form.setUserType(userType);
        return form;
    }

    private static MockHttpServletRequest request(String ip, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        request.addHeader("User-Agent", userAgent);
        return request;
    }
}
