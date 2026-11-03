package com.recruit.auth.service;

import com.recruit.auth.dto.MeView;
import com.recruit.auth.dto.RegisterForm;
import com.recruit.auth.dto.RegisterResult;
import com.recruit.auth.entity.IamRole;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.entity.IamUserRole;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.mapper.IamUserRoleMapper;
import com.recruit.auth.support.IpUtil;
import com.recruit.auth.support.PhoneCipher;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 账号自助能力：候选人注册（A-02）与当前用户视图（A-05）。
 *
 * <p>注册只落 {@code iam_user} + {@code iam_user_role(CANDIDATE)}；
 * 员工账号仍由 {@code AdminUserService} 创建，避免公开接口越权造员工。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String USER_TYPE_CANDIDATE = "CANDIDATE";
    private static final String ROLE_CANDIDATE = "CANDIDATE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String ACTION_REGISTER = "REGISTER";
    private static final String RESOURCE_USER = "USER";
    private static final String SCOPE_REGISTER = "auth.register";

    private final IamUserMapper userMapper;
    private final IamRoleMapper roleMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final IamDepartmentMapper departmentMapper;
    private final PermissionService permissionService;
    private final MustChangePasswordService mustChangePasswordService;
    private final AuditService auditService;
    private final PhoneCipher phoneCipher;
    private final PasswordEncoder passwordEncoder;
    private final IdempotencyService idempotencyService;

    /**
     * 候选人自助注册。
     *
     * @param form    注册入参
     * @param key     Idempotency-Key，可为空
     * @param request HTTP 请求（取 IP 写审计）
     * @return 新用户摘要
     */
    @Transactional
    public RegisterResult register(RegisterForm form, String key, HttpServletRequest request) {
        if (key != null && !key.isBlank()) {
            return idempotencyService.execute(SCOPE_REGISTER, key, RegisterResult.class,
                    () -> doRegister(form, request));
        }
        return doRegister(form, request);
    }

    private RegisterResult doRegister(RegisterForm form, HttpServletRequest request) {
        String username = form.getUsername().trim();
        if (userMapper.countByUsername(username) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名已存在");
        }

        IamRole candidateRole = roleMapper.selectByRoleCode(ROLE_CANDIDATE);
        if (candidateRole == null || !STATUS_ACTIVE.equals(candidateRole.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "候选人角色未初始化，暂不能注册");
        }

        String phoneHash = null;
        String phoneEnc = null;
        if (form.getPhone() != null && !form.getPhone().isBlank()) {
            phoneHash = phoneCipher.hash(form.getPhone());
            if (userMapper.countByPhoneHash(phoneHash) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "手机号已被使用");
            }
            try {
                phoneEnc = phoneCipher.encrypt(form.getPhone());
            } catch (IllegalStateException e) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "未配置手机号加密密钥（PHONE_ENC_KEY），暂不能保存手机号");
            }
        }

        IamUser user = new IamUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setUserType(USER_TYPE_CANDIDATE);
        user.setDisplayName(form.getDisplayName().trim());
        user.setStatus(STATUS_ACTIVE);
        user.setTokenVersion(0);
        user.setPhoneEnc(phoneEnc);
        user.setPhoneHash(phoneHash);
        user.setVersion(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名或手机号已存在");
        }

        IamUserRole relation = new IamUserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(candidateRole.getId());
        userRoleMapper.insert(relation);

        String userId = String.valueOf(user.getId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userType", USER_TYPE_CANDIDATE);
        detail.put("roleCode", ROLE_CANDIDATE);
        auditService.recordQuietly(user.getId(), ACTION_REGISTER, RESOURCE_USER, userId,
                true, IpUtil.clientIp(request), detail);
        log.info("audit action=REGISTER operatorId={} username={}", userId, username);
        return new RegisterResult(userId, username, user.getDisplayName(), USER_TYPE_CANDIDATE);
    }

    /**
     * 当前用户视图。
     *
     * @param userId 当前用户 ID
     * @return 用户、角色、权限码、数据范围与脱敏手机号
     */
    public MeView me(long userId) {
        IamUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        Long deptId = user.getDeptId();
        List<Long> deptIds = deptId == null ? List.of() : departmentMapper.selectSubtreeIds(deptId);
        String phone = phoneCipher.decrypt(user.getPhoneEnc());
        return new MeView(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getDisplayName(),
                user.getUserType(),
                phoneCipher.mask(phone),
                deptId == null ? null : String.valueOf(deptId),
                deptIds.stream().map(String::valueOf).toList(),
                permissionService.roleCodes(userId),
                permissionService.permissionCodes(userId),
                permissionService.dataScope(userId),
                mustChangePasswordService.isMarked(userId));
    }
}
