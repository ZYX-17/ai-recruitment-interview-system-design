package com.recruit.auth.service;

import com.recruit.auth.dto.ChangePasswordForm;
import com.recruit.auth.dto.LoginForm;
import com.recruit.auth.dto.LoginResult;
import com.recruit.auth.dto.RefreshResult;
import com.recruit.auth.dto.UserSummary;
import com.recruit.auth.entity.IamDepartment;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.security.JwtClaims;
import com.recruit.auth.security.UserPrincipal;
import com.recruit.auth.support.IpUtil;
import com.recruit.common.constant.RedisKeys;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证编排：登录（A-01）、登出（A-04）、自助改密（P1-5）。
 *
 * <p>安全口径：</p>
 * <ul>
 *   <li>用户不存在/密码错误/已锁定统一 {@code AUTH1001}，不泄露账号是否存在；</li>
 *   <li>用户不存在时也跑一次 BCrypt，避免用响应时间枚举账号；</li>
 *   <li>状态非 ACTIVE 返回 {@code AUTH1101}，不签发令牌；</li>
 *   <li>成功后清零失败计数、回填 Redis {@code auth:tv:{userId}}，再签发双令牌。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ACTION_LOGIN = "LOGIN";
    private static final String ACTION_LOGOUT = "LOGOUT";
    private static final String ACTION_CHANGE_PASSWORD = "PASSWORD_CHANGE";
    private static final String RESOURCE_USER = "USER";

    /**
     * 固定 BCrypt 哈希，仅用于“用户不存在”时消耗同等 CPU 时间。
     * 明文不是任何真实密码，matches 结果只用于拖时间。
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final IamUserMapper userMapper;
    private final IamDepartmentMapper departmentMapper;
    private final PermissionService permissionService;
    private final TokenService tokenService;
    private final RefreshSessionService refreshSessionService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final TokenRevocationService tokenRevocationService;

    /**
     * 登录。
     *
     * <p>不加 {@code @Transactional}：失败路径要写审计后抛 401，
     * 若与业务同事务会被回滚；会话落库由 {@link RefreshSessionService#issue} 自己开事务。</p>
     *
     * @param form    登录入参
     * @param request HTTP 请求（取 IP / User-Agent）
     * @return 登录结果（含 Refresh Token 原文，由 Controller 决定是否放响应体）
     */
    public LoginResult login(LoginForm form, HttpServletRequest request) {
        String username = form.getUsername().trim();
        String ip = IpUtil.clientIp(request);
        String device = request.getHeader("User-Agent");

        if (loginAttemptService.isLocked(username)) {
            audit(null, null, false, ip, "LOCKED", null);
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        IamUser user = userMapper.selectByUsername(username);
        if (user == null) {
            passwordEncoder.matches(form.getPassword(), DUMMY_HASH);
            long failures = loginAttemptService.recordFailure(username);
            audit(null, null, false, ip, "USER_NOT_FOUND", failures);
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        String userId = String.valueOf(user.getId());
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            audit(user.getId(), userId, false, ip, "STATUS_" + safeStatus(user.getStatus()), null);
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "账号已禁用或锁定，请联系管理员");
        }

        if (hasText(form.getUserType()) && !form.getUserType().equalsIgnoreCase(user.getUserType())) {
            long failures = loginAttemptService.recordFailure(username);
            audit(user.getId(), userId, false, ip, "USER_TYPE_MISMATCH", failures);
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        // 候选人可能还没有密码；仍跑一次 BCrypt，避免 null 哈希告警并保持耗时一致
        String passwordHash = hasText(user.getPasswordHash()) ? user.getPasswordHash() : DUMMY_HASH;
        if (!passwordEncoder.matches(form.getPassword(), passwordHash)) {
            long failures = loginAttemptService.recordFailure(username);
            audit(user.getId(), userId, false, ip, "PASSWORD_MISMATCH", failures);
            throw new BizException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        loginAttemptService.reset(username);

        List<String> roles = permissionService.roleCodes(user.getId());
        List<String> permissions = permissionService.permissionCodes(user.getId());
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();

        // DB 是 token_version 的权威来源；登录时回填 Redis，网关缺失 Key 时按 fail-closed 处理
        redisTemplate.opsForValue().set(RedisKeys.authTokenVersion(userId), Long.toString(tokenVersion));

        String accessToken = tokenService.createAccessToken(user, roles, tokenVersion);
        RefreshSessionService.IssuedRefresh refresh =
                refreshSessionService.issue(user, roles, tokenVersion, ip, device);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("roles", roles);
        auditService.recordQuietly(user.getId(), ACTION_LOGIN, RESOURCE_USER, userId, true, ip, detail);

        return new LoginResult(accessToken, "Bearer", tokenService.accessTtlSeconds(), refresh.token(),
                toSummary(user), roles, permissions, tokenVersion);
    }

    /**
     * 刷新 Access Token（A-03）。
     *
     * <p>验签、类型、family 存在性、Redis 与 DB 的 token_version、GETDEL 原子消费
     * 都在 {@link RefreshSessionService#rotate} 内完成；这里只负责取用户与组装响应。</p>
     *
     * @param refreshToken Refresh Token（Cookie 或 local body）
     * @param request      HTTP 请求（取 IP / User-Agent）
     * @return 新 Access Token 与新 Refresh Token
     */
    public RefreshResult refresh(String refreshToken, HttpServletRequest request) {
        if (!hasText(refreshToken)) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        JwtClaims claims;
        try {
            claims = tokenService.parseRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        long userId;
        try {
            userId = Long.parseLong(claims.userId());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        IamUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.AUTH_REFRESH_INVALID);
        }

        List<String> roles = permissionService.roleCodes(userId);
        long tokenVersion = user.getTokenVersion() == null ? 0L : user.getTokenVersion();
        RefreshSessionService.IssuedRefresh issued = refreshSessionService.rotate(
                refreshToken, claims, user, roles, tokenVersion,
                IpUtil.clientIp(request), request.getHeader("User-Agent"));

        String accessToken = tokenService.createAccessToken(user, roles, tokenVersion);
        return new RefreshResult(accessToken, "Bearer", tokenService.accessTtlSeconds(), issued.token());
    }

    /**
     * 登出（A-04）。
     *
     * <p>幂等：令牌已失效、Cookie 缺失或已撤销都返回成功，不把重复登出变成 401。
     * 当前设备登出撤销 Refresh Cookie 所属 family；{@code allDevices=true} 递增
     * token_version 并撤销该用户全部 Refresh 会话。</p>
     *
     * @param principal    当前登录主体，可为 null（logout 端点白名单放行以便幂等）
     * @param refreshToken Refresh Cookie 原文，可为 null
     * @param allDevices   是否全端登出
     * @param request      HTTP 请求（取 IP）
     */
    public void logout(UserPrincipal principal, String refreshToken, boolean allDevices,
                       HttpServletRequest request) {
        String userId = principal == null ? null : principal.getUserId();
        if (allDevices && userId == null && hasText(refreshToken)) {
            userId = refreshUserId(refreshToken);
        }

        if (principal != null) {
            tokenRevocationService.blacklist(principal.getJti(), principal.getExpiresAt());
        }
        Long operatorId = parseUserId(userId);
        if (allDevices && operatorId != null) {
            tokenRevocationService.revokeAllSessions(operatorId);
        } else if (hasText(refreshToken)) {
            refreshSessionService.revoke(refreshToken);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("allDevices", allDevices);
        auditService.recordQuietly(operatorId, ACTION_LOGOUT, RESOURCE_USER, userId, true,
                IpUtil.clientIp(request), detail);
    }

    /**
     * 自助改密（P1-5）。
     *
     * <p>校验原密码 → 更新 BCrypt 哈希 → 递增 token_version 并撤销全部会话。
     * 事务覆盖密码更新与会话撤销，避免出现“密码已改但旧会话仍有效”的中间态。</p>
     *
     * @param userId  当前用户 ID
     * @param form    改密入参
     * @param request HTTP 请求（取 IP）
     */
    @Transactional
    public void changePassword(long userId, ChangePasswordForm form, HttpServletRequest request) {
        IamUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        String resourceId = String.valueOf(userId);
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            auditChangePassword(user.getId(), resourceId, false, request, "STATUS_" + safeStatus(user.getStatus()));
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "账号已禁用或锁定，请联系管理员");
        }

        String currentHash = hasText(user.getPasswordHash()) ? user.getPasswordHash() : DUMMY_HASH;
        if (!hasText(user.getPasswordHash())
                || !passwordEncoder.matches(form.getOldPassword(), currentHash)) {
            auditChangePassword(user.getId(), resourceId, false, request, "OLD_PASSWORD_MISMATCH");
            throw new BizException(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(form.getNewPassword(), currentHash)) {
            auditChangePassword(user.getId(), resourceId, false, request, "SAME_AS_OLD");
            throw new BizException(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
        }

        int updated = userMapper.updatePasswordHash(userId, passwordEncoder.encode(form.getNewPassword()));
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        tokenRevocationService.revokeAllSessions(userId);
        auditChangePassword(user.getId(), resourceId, true, request, "OK");
    }

    private void auditChangePassword(Long operatorId, String resourceId, boolean success,
                                     HttpServletRequest request, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", reason);
        auditService.recordQuietly(operatorId, ACTION_CHANGE_PASSWORD, RESOURCE_USER, resourceId,
                success, IpUtil.clientIp(request), detail);
    }

    private String refreshUserId(String refreshToken) {
        try {
            return tokenService.parseRefreshToken(refreshToken).userId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long parseUserId(String userId) {
        if (!hasText(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UserSummary toSummary(IamUser user) {
        String deptId = null;
        String deptName = null;
        if (user.getDeptId() != null) {
            deptId = String.valueOf(user.getDeptId());
            IamDepartment department = departmentMapper.selectById(user.getDeptId());
            if (department != null && STATUS_ACTIVE.equals(department.getStatus())) {
                deptName = department.getDeptName();
            }
        }
        return new UserSummary(String.valueOf(user.getId()), user.getUsername(), user.getDisplayName(),
                user.getUserType(), null, deptId, deptName);
    }

    private void audit(Long operatorId, String resourceId, boolean success, String ip,
                       String reason, Long failures) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", reason);
        if (failures != null) {
            detail.put("failures", failures);
        }
        auditService.recordQuietly(operatorId, ACTION_LOGIN, RESOURCE_USER, resourceId, success, ip, detail);
    }

    private static String safeStatus(String status) {
        return status == null || status.isBlank() ? "UNKNOWN" : status;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
