package com.recruit.auth.service;

import com.recruit.auth.dto.AdminUserView;
import com.recruit.auth.dto.AssignRolesForm;
import com.recruit.auth.dto.CreateUserForm;
import com.recruit.auth.dto.CreatedUserResult;
import com.recruit.auth.dto.ResetPasswordResult;
import com.recruit.auth.dto.RoleBrief;
import com.recruit.auth.dto.UpdateUserForm;
import com.recruit.auth.dto.UserPageQuery;
import com.recruit.auth.entity.IamDepartment;
import com.recruit.auth.entity.IamUser;
import com.recruit.auth.entity.IamUserRole;
import com.recruit.auth.mapper.IamDepartmentMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.auth.mapper.IamUserMapper;
import com.recruit.auth.mapper.IamUserRoleMapper;
import com.recruit.auth.mapper.row.AdminUserRow;
import com.recruit.auth.mapper.row.UserRoleRow;
import com.recruit.auth.support.PhoneCipher;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理（P2-1：A-07~A-10、X-09、X-10）。
 *
 * <p>关键口径：</p>
 * <ul>
 *   <li>列表/详情按角色数据范围过滤：ALL 不过滤，DEPARTMENT 限本部门及子部门，其余按本人收口；</li>
 *   <li>删除是软删（{@code status=DISABLED}），{@code V1_0} 没有 deleted 列；</li>
 *   <li>不能删除自己、不能删除最后一个可用 SUPER_ADMIN；</li>
 *   <li>更新走 {@code version} 乐观锁，冲突返回 {@code SYS412}；</li>
 *   <li>重置密码返回一次性临时密码，并标记下次登录强制改密。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED");
    private static final Set<String> USER_TYPES = Set.of("EMPLOYEE", "CANDIDATE");

    private static final String RESOURCE_USER = "USER";
    private static final String ACTION_CREATE = "USER_CREATE";
    private static final String ACTION_UPDATE = "USER_UPDATE";
    private static final String ACTION_DELETE = "USER_DELETE";
    private static final String ACTION_RESET_PASSWORD = "USER_RESET_PASSWORD";
    private static final String ACTION_ASSIGN_ROLE = "USER_ASSIGN_ROLE";
    private static final String ACTION_KICK = "USER_KICK";
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    private static final int PASSWORD_MIN_LENGTH = 8;
    private static final int PASSWORD_MAX_LENGTH = 128;
    private static final int TEMP_PASSWORD_LENGTH = 16;
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";

    private final IamUserMapper userMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final IamRoleMapper roleMapper;
    private final IamDepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final PhoneCipher phoneCipher;
    private final TokenRevocationService tokenRevocationService;
    private final MustChangePasswordService mustChangePasswordService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    private final SecureRandom random = new SecureRandom();

    /**
     * 用户分页（A-07）。
     *
     * @param query      分页与过滤条件
     * @param operatorId 当前操作者 ID
     * @return 分页用户
     */
    public PageResult<AdminUserView> page(UserPageQuery query, long operatorId) {
        String keyword = trimToNull(query.getKeyword());
        String status = trimToNull(query.getStatus());
        DataScopeFilter scope = resolveScope(operatorId);

        long total = userMapper.countAdminPage(keyword, status, query.getDeptId(),
                scope.deptRootId(), scope.onlyUserId());
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }

        long offset = (query.getPageNum() - 1) * query.getPageSize();
        List<AdminUserRow> rows = userMapper.selectAdminPage(keyword, status, query.getDeptId(),
                scope.deptRootId(), scope.onlyUserId(), query.getPageSize(), offset);
        Map<Long, List<RoleBrief>> roles = loadRoles(rows.stream().map(AdminUserRow::getId).toList());

        List<AdminUserView> views = new ArrayList<>(rows.size());
        for (AdminUserRow row : rows) {
            views.add(toView(row, roles.getOrDefault(row.getId(), List.of())));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 用户详情（X-09）。
     *
     * @param id         用户 ID
     * @param operatorId 当前操作者 ID
     * @return 用户详情
     */
    public AdminUserView detail(long id, long operatorId) {
        DataScopeFilter scope = resolveScope(operatorId);
        if (userMapper.countVisibleById(id, scope.deptRootId(), scope.onlyUserId()) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return viewOf(id);
    }

    /**
     * 新增用户（A-08）。
     *
     * <p>管理员未指定初始密码时生成一次性临时密码；两种情况都要求首次登录改密，
     * 响应中的明文密码只出现这一次。</p>
     *
     * @param form       新增入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 新用户 ID 与初始密码
     */
    @Transactional
    public CreatedUserResult create(CreateUserForm form, long operatorId, String ip) {
        String username = form.getUsername().trim();
        if (userMapper.countByUsername(username) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名已存在");
        }

        String userType = requireOneOf(form.getUserType(), USER_TYPES, "用户类型非法");
        String status = trimToNull(form.getStatus()) == null
                ? STATUS_ACTIVE
                : requireOneOf(form.getStatus(), USER_STATUSES, "用户状态非法");
        Long deptId = requireActiveDepartment(form.getDeptId());
        List<Long> roleIds = distinctIds(form.getRoleIds());
        requireActiveRoles(roleIds);

        String phoneHash = null;
        String phoneEnc = null;
        if (hasText(form.getPhone())) {
            phoneHash = phoneCipher.hash(form.getPhone());
            if (userMapper.countByPhoneHash(phoneHash) > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "手机号已被使用");
            }
            try {
                phoneEnc = phoneCipher.encrypt(form.getPhone());
            } catch (IllegalStateException e) {
                throw new BizException(ErrorCode.PARAM_INVALID, "未配置手机号加密密钥（PHONE_ENC_KEY），暂不能保存手机号");
            }
        }

        boolean generated = !hasText(form.getInitialPassword());
        String initialPassword = generated ? generatePassword() : form.getInitialPassword();
        validatePassword(initialPassword);

        IamUser user = new IamUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setUserType(userType);
        user.setDisplayName(form.getDisplayName().trim());
        user.setStatus(status);
        user.setTokenVersion(0);
        user.setPhoneEnc(phoneEnc);
        user.setPhoneHash(phoneHash);
        user.setDeptId(deptId);
        user.setVersion(0);
        userMapper.insert(user);

        insertRoles(user.getId(), roleIds);
        mustChangePasswordService.mark(user.getId());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userType", userType);
        detail.put("roleIds", roleIds);
        detail.put("generatedPassword", generated);
        auditService.recordQuietly(operatorId, ACTION_CREATE, RESOURCE_USER,
                String.valueOf(user.getId()), true, ip, detail);

        return new CreatedUserResult(String.valueOf(user.getId()), initialPassword, true);
    }

    /**
     * 更新用户白名单字段（A-09）。
     *
     * @param id         用户 ID
     * @param form       更新入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 更新后的用户
     */
    @Transactional
    public AdminUserView update(long id, UpdateUserForm form, long operatorId, String ip) {
        IamUser current = requireUser(id);

        String displayName = hasText(form.getDisplayName())
                ? form.getDisplayName().trim() : current.getDisplayName();
        Long deptId = form.getDeptId() == null ? current.getDeptId() : requireActiveDepartment(form.getDeptId());
        String status = trimToNull(form.getStatus()) == null
                ? current.getStatus()
                : requireOneOf(form.getStatus(), USER_STATUSES, "用户状态非法");

        if (!STATUS_ACTIVE.equals(status) && STATUS_ACTIVE.equals(current.getStatus())
                && userMapper.countSuperAdminRoles(id) > 0
                && userMapper.countOtherActiveSuperAdmins(id) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能停用最后一个超级管理员");
        }

        int updated = userMapper.updateProfile(id, displayName, deptId, status, form.getVersion());
        if (updated == 0) {
            if (userMapper.selectById(id) == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
            }
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        if (!STATUS_ACTIVE.equals(status)) {
            tokenRevocationService.revokeAllSessions(id);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", status);
        detail.put("deptId", deptId);
        auditService.recordQuietly(operatorId, ACTION_UPDATE, RESOURCE_USER,
                String.valueOf(id), true, ip, detail);

        return detail(id, operatorId);
    }

    /**
     * 软删用户（A-10）。
     *
     * <p>置 {@code DISABLED} 并撤销全部会话；被删除用户的登录名仍占用唯一索引，
     * 便于审计追溯（V1_0 没有 deleted 列）。</p>
     *
     * @param id         用户 ID
     * @param operatorId 当前操作者 ID（不能等于目标用户）
     * @param ip         客户端 IP，写审计用
     */
    @Transactional
    public void delete(long id, long operatorId, String ip) {
        if (id == operatorId) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能删除自己的账号");
        }
        IamUser user = requireUser(id);
        if (userMapper.countSuperAdminRoles(id) > 0 && userMapper.countOtherActiveSuperAdmins(id) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能删除最后一个超级管理员");
        }
        if (!STATUS_DISABLED.equals(user.getStatus())) {
            userMapper.softDelete(id);
        }
        tokenRevocationService.revokeAllSessions(id);
        auditService.recordQuietly(operatorId, ACTION_DELETE, RESOURCE_USER,
                String.valueOf(id), true, ip, Map.of("status", STATUS_DISABLED));
    }

    /**
     * 管理员重置密码（X-10）。
     *
     * <p>返回一次性临时密码，同时撤销全部会话并标记下次登录强制改密。</p>
     *
     * @param id         用户 ID
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 临时密码
     */
    @Transactional
    public ResetPasswordResult resetPassword(long id, long operatorId, String ip) {
        requireUser(id);
        String temporaryPassword = generatePassword();
        userMapper.updatePasswordHash(id, passwordEncoder.encode(temporaryPassword));
        tokenRevocationService.revokeAllSessions(id);
        mustChangePasswordService.mark(id);

        auditService.recordQuietly(operatorId, ACTION_RESET_PASSWORD, RESOURCE_USER,
                String.valueOf(id), true, ip, Map.of("temporary", true));
        return new ResetPasswordResult(temporaryPassword, true);
    }

    /**
     * 全量替换用户角色（A-11）。
     *
     * <p>角色决定令牌里的角色声明、数据范围与菜单，变更后旧令牌必须失效：递增
     * {@code token_version} 撤销全部会话，并递增 {@code auth:permver} 让权限缓存失效。
     * 同时保护最后一个可用 {@code SUPER_ADMIN}，避免把系统锁死。</p>
     *
     * @param id         用户 ID
     * @param form       角色入参（全量替换，{@code version} 可选）
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 更新后的用户
     */
    @Transactional
    public AdminUserView assignRoles(long id, AssignRolesForm form, long operatorId, String ip) {
        requireUser(id);
        List<Long> roleIds = distinctIds(form.getRoleIds());
        requireActiveRoles(roleIds);

        boolean hadSuperAdmin = roleMapper.selectActiveRoleCodesByUserId(id).contains(ROLE_SUPER_ADMIN);
        if (hadSuperAdmin && !containsSuperAdmin(roleIds)
                && userMapper.countOtherActiveSuperAdmins(id) == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能移除最后一个超级管理员的角色");
        }

        if (form.getVersion() != null && userMapper.incrementVersion(id, form.getVersion()) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        permissionService.bumpVersion(id);
        userRoleMapper.deleteByUserId(id);
        insertRoles(id, roleIds);
        tokenRevocationService.revokeAllSessions(id);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("roleIds", roleIds);
        auditService.recordQuietly(operatorId, ACTION_ASSIGN_ROLE, RESOURCE_USER,
                String.valueOf(id), true, ip, detail);

        return viewOf(id);
    }

    /**
     * 强制下线（X-10，幂等）。
     *
     * <p>递增 {@code token_version} 并撤销全部 Refresh 会话；重复调用只会继续递增，
     * 结果仍是“该用户已签发的令牌全部失效”，因此天然幂等。</p>
     *
     * @param id         用户 ID
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     */
    @Transactional
    public void kick(long id, long operatorId, String ip) {
        requireUser(id);
        long tokenVersion = tokenRevocationService.revokeAllSessions(id);
        auditService.recordQuietly(operatorId, ACTION_KICK, RESOURCE_USER,
                String.valueOf(id), true, ip, Map.of("tokenVersion", tokenVersion));
    }

    private AdminUserView viewOf(long id) {
        AdminUserRow row = loadRow(id);
        return toView(row, loadRoles(List.of(id)).getOrDefault(id, List.of()));
    }

    private AdminUserRow loadRow(long id) {
        AdminUserRow row = userMapper.selectAdminRowById(id);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return row;
    }

    private IamUser requireUser(long id) {
        IamUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private Map<Long, List<RoleBrief>> loadRoles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRoleRow> rows = userMapper.selectRolesByUserIds(userIds);
        Map<Long, List<RoleBrief>> grouped = new LinkedHashMap<>();
        for (UserRoleRow row : rows) {
            grouped.computeIfAbsent(row.getUserId(), key -> new ArrayList<>())
                    .add(new RoleBrief(String.valueOf(row.getRoleId()), row.getRoleCode(), row.getRoleName()));
        }
        return grouped;
    }

    private AdminUserView toView(AdminUserRow row, List<RoleBrief> roles) {
        String phone = phoneCipher.mask(phoneCipher.decrypt(row.getPhoneEnc()));
        return new AdminUserView(
                String.valueOf(row.getId()),
                row.getUsername(),
                row.getDisplayName(),
                row.getUserType(),
                row.getStatus(),
                phone,
                row.getDeptId() == null ? null : String.valueOf(row.getDeptId()),
                row.getDeptName(),
                roles,
                row.getCreatedAt(),
                row.getVersion() == null ? 0 : row.getVersion());
    }

    private void insertRoles(Long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            IamUserRole relation = new IamUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    private void requireActiveRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        long found = roleMapper.countActiveByIds(roleIds);
        if (found != roleIds.size()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色不存在或已停用");
        }
    }

    private boolean containsSuperAdmin(List<Long> roleIds) {
        return !roleIds.isEmpty() && roleMapper.countSuperAdminInIds(roleIds) > 0;
    }

    private Long requireActiveDepartment(Long deptId) {
        if (deptId == null) {
            return null;
        }
        IamDepartment department = departmentMapper.selectById(deptId);
        if (department == null || !STATUS_ACTIVE.equals(department.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门不存在或已停用");
        }
        return deptId;
    }

    private DataScopeFilter resolveScope(long operatorId) {
        List<String> scopes = roleMapper.selectActiveDataScopesByUserId(operatorId);
        if (scopes.contains("ALL")) {
            return new DataScopeFilter(null, null);
        }
        if (scopes.contains("DEPARTMENT")) {
            IamUser operator = userMapper.selectById(operatorId);
            Long deptId = operator == null ? null : operator.getDeptId();
            return deptId == null ? new DataScopeFilter(null, operatorId) : new DataScopeFilter(deptId, null);
        }
        // ASSIGNED 在用户管理里没有“被分配用户”的语义，按 SELF 收口，避免越权看到全量
        return new DataScopeFilter(null, operatorId);
    }

    private String generatePassword() {
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
        builder.append("ABCDEFGHJKLMNPQRSTUVWXYZ".charAt(random.nextInt(24)));
        builder.append("abcdefghijkmnpqrstuvwxyz".charAt(random.nextInt(24)));
        builder.append("23456789".charAt(random.nextInt(8)));
        builder.append("!@#$%".charAt(random.nextInt(5)));
        while (builder.length() < TEMP_PASSWORD_LENGTH) {
            builder.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        List<Character> chars = new ArrayList<>(builder.length());
        for (char c : builder.toString().toCharArray()) {
            chars.add(c);
        }
        Collections.shuffle(chars, random);
        StringBuilder shuffled = new StringBuilder(chars.size());
        chars.forEach(shuffled::append);
        return shuffled.toString();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码长度需在 8~128 之间");
        }
    }

    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(id -> id != null).toList()));
    }

    private static String requireOneOf(String value, Set<String> allowed, String message) {
        String text = value == null ? null : value.trim().toUpperCase();
        if (text == null || !allowed.contains(text)) {
            throw new BizException(ErrorCode.PARAM_INVALID, message);
        }
        return text;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 数据范围过滤条件：两者都为 null 表示 ALL。 */
    private record DataScopeFilter(Long deptRootId, Long onlyUserId) {
    }
}
