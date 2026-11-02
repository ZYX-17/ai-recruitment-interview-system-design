package com.recruit.auth.service;

import com.recruit.auth.dto.LoginForm;
import com.recruit.auth.dto.LoginResult;
import com.recruit.auth.entity.IamDepartment;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamUserMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
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

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, departmentMapper, permissionService, tokenService,
                refreshSessionService, loginAttemptService, auditService, passwordEncoder, redisTemplate);
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
