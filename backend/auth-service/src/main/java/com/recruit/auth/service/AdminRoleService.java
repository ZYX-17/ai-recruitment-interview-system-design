package com.recruit.auth.service;

import com.recruit.auth.dto.AssignPermissionsForm;
import com.recruit.auth.dto.CreateRoleForm;
import com.recruit.auth.dto.RoleDetailView;
import com.recruit.auth.dto.RolePageQuery;
import com.recruit.auth.dto.RoleView;
import com.recruit.auth.dto.UpdateRoleForm;
import com.recruit.auth.entity.IamRole;
import com.recruit.auth.entity.IamRolePermission;
import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.auth.mapper.IamRoleMapper;
import com.recruit.auth.mapper.IamRolePermissionMapper;
import com.recruit.auth.mapper.IamUserRoleMapper;
import com.recruit.auth.mapper.row.AdminRoleRow;
import com.recruit.auth.mapper.row.PermissionBriefRow;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 角色管理（P2-3：A-12/A-13/A-14 与角色详情）。
 *
 * <p>关键口径：</p>
 * <ul>
 *   <li>系统内置角色（seed 的五个）不可删除；其数据范围与启停状态也不允许在线改，避免把超管降权或停用；</li>
 *   <li>角色编码全局唯一，落库前统一转大写；</li>
 *   <li>仍有用户绑定的角色不可删除（先改派角色再删）；</li>
 *   <li>分配权限全量替换；权限变更后按绑定用户递增 {@code auth:permver} 并清理权限缓存；</li>
 *   <li>SUPER_ADMIN 的权限集合由 seed 维护，禁止在线修改，防止把最后一个管理员锁死。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> DATA_SCOPES = Set.of("ALL", "DEPARTMENT", "ASSIGNED", "SELF");
    private static final Set<String> ROLE_STATUSES = Set.of("ACTIVE", "DISABLED");

    private static final String RESOURCE_ROLE = "ROLE";
    private static final String ACTION_CREATE = "ROLE_CREATE";
    private static final String ACTION_UPDATE = "ROLE_UPDATE";
    private static final String ACTION_DELETE = "ROLE_DELETE";
    private static final String ACTION_ASSIGN_PERMISSION = "ROLE_ASSIGN_PERMISSION";

    private final IamRoleMapper roleMapper;
    private final IamRolePermissionMapper rolePermissionMapper;
    private final IamPermissionMapper permissionMapper;
    private final IamUserRoleMapper userRoleMapper;
    private final PermissionService permissionService;
    private final AuditService auditService;

    /**
     * 角色分页（A-12）。
     *
     * @param query 分页与过滤条件
     * @return 分页角色
     */
    public PageResult<RoleView> page(RolePageQuery query) {
        String keyword = trimToNull(query.getKeyword());
        String status = trimToNull(query.getStatus());

        long total = roleMapper.countRolePage(keyword, status);
        if (total == 0) {
            return PageResult.of(List.of(), 0, query.getPageNum(), query.getPageSize());
        }
        long offset = (query.getPageNum() - 1) * query.getPageSize();
        List<AdminRoleRow> rows = roleMapper.selectRolePage(keyword, status, query.getPageSize(), offset);

        List<RoleView> views = new ArrayList<>(rows.size());
        for (AdminRoleRow row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 角色详情（X-09）：带已勾选的权限 ID。
     *
     * @param id 角色 ID
     * @return 角色详情
     */
    public RoleDetailView detail(long id) {
        AdminRoleRow row = loadRow(id);
        return toDetail(row, rolePermissionMapper.selectPermissionIdsByRoleId(id));
    }

    /**
     * 新增角色（A-12）。
     *
     * @param form       新增入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 新角色
     */
    @Transactional
    public RoleView create(CreateRoleForm form, long operatorId, String ip) {
        String roleCode = form.getRoleCode().trim().toUpperCase(Locale.ROOT);
        if (roleMapper.selectByRoleCode(roleCode) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色编码已存在");
        }
        String dataScope = requireOneOf(form.getDataScope(), DATA_SCOPES, "数据范围非法");
        boolean systemRole = Boolean.TRUE.equals(form.getIsSystemRole());
        if (systemRole && !isSuperAdmin(operatorId)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "只有超级管理员可以创建系统内置角色");
        }

        IamRole role = new IamRole();
        role.setRoleCode(roleCode);
        role.setRoleName(form.getRoleName().trim());
        role.setDataScope(dataScope);
        role.setIsSystemRole(systemRole);
        role.setStatus(STATUS_ACTIVE);
        role.setRemark(trimToNull(form.getRemark()));
        role.setVersion(0);
        roleMapper.insert(role);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("roleCode", roleCode);
        detail.put("dataScope", dataScope);
        detail.put("isSystemRole", systemRole);
        auditService.recordQuietly(operatorId, ACTION_CREATE, RESOURCE_ROLE,
                String.valueOf(role.getId()), true, ip, detail);

        return toView(loadRow(role.getId()));
    }

    /**
     * 修改角色（A-13）。
     *
     * <p>系统内置角色只允许改角色名与备注：数据范围/启停由 seed 维护，
     * 避免把 SUPER_ADMIN 的 ALL 范围改小或把内置角色停用。</p>
     *
     * @param id         角色 ID
     * @param form       修改入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 更新后的角色
     */
    @Transactional
    public RoleView update(long id, UpdateRoleForm form, long operatorId, String ip) {
        AdminRoleRow current = loadRow(id);
        boolean systemRole = Boolean.TRUE.equals(current.getIsSystemRole());

        String roleName = hasText(form.getRoleName()) ? form.getRoleName().trim() : current.getRoleName();
        String dataScope = trimToNull(form.getDataScope()) == null
                ? current.getDataScope()
                : requireOneOf(form.getDataScope(), DATA_SCOPES, "数据范围非法");
        String status = trimToNull(form.getStatus()) == null
                ? current.getStatus()
                : requireOneOf(form.getStatus(), ROLE_STATUSES, "角色状态非法");
        String remark = form.getRemark() == null ? current.getRemark() : trimToNull(form.getRemark());

        if (systemRole && !dataScope.equals(current.getDataScope())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "系统内置角色不允许修改数据范围");
        }
        if (systemRole && !status.equals(current.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "系统内置角色不允许启停");
        }

        if (roleMapper.updateRole(id, roleName, dataScope, status, remark, form.getVersion()) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("dataScope", dataScope);
        detail.put("status", status);
        auditService.recordQuietly(operatorId, ACTION_UPDATE, RESOURCE_ROLE,
                String.valueOf(id), true, ip, detail);

        return toView(loadRow(id));
    }

    /**
     * 删除角色（A-13）。
     *
     * <p>系统内置角色与仍有用户绑定的角色都拒绝删除；删除时一并清理角色-权限关联。</p>
     *
     * @param id         角色 ID
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     */
    @Transactional
    public void delete(long id, long operatorId, String ip) {
        AdminRoleRow role = loadRow(id);
        if (Boolean.TRUE.equals(role.getIsSystemRole())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "系统内置角色不可删除");
        }
        if (userRoleMapper.countByRoleId(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仍有用户绑定该角色，请先改派角色");
        }

        rolePermissionMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);

        auditService.recordQuietly(operatorId, ACTION_DELETE, RESOURCE_ROLE,
                String.valueOf(id), true, ip, Map.of("roleCode", role.getRoleCode()));
    }

    /**
     * 全量替换角色权限（A-14）。
     *
     * <p>{@code menusOnly=true} 对应 {@code /menus}，只允许勾选 MENU 类型；
     * 权限树要求父节点同时提交，避免出现“有按钮没菜单”的不可见授权。
     * 变更后按绑定用户逐个递增权限版本并清理缓存，下一次请求即生效。</p>
     *
     * @param roleId       角色 ID
     * @param form         权限入参
     * @param menusOnly    是否只允许 MENU 类型
     * @param operatorId   当前操作者 ID
     * @param ip           客户端 IP，写审计用
     * @return 更新后的角色详情
     */
    @Transactional
    public RoleDetailView assignPermissions(long roleId, AssignPermissionsForm form, boolean menusOnly,
                                            long operatorId, String ip) {
        AdminRoleRow role = loadRow(roleId);
        if (ROLE_SUPER_ADMIN.equals(role.getRoleCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "超级管理员角色权限由系统维护，不允许在线修改");
        }

        List<Long> permissionIds = distinctIds(form.getPermissionIds());
        requireAssignablePermissions(permissionIds, menusOnly);

        if (form.getVersion() != null && roleMapper.incrementVersion(roleId, form.getVersion()) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        rolePermissionMapper.deleteByRoleId(roleId);
        for (Long permissionId : permissionIds) {
            IamRolePermission relation = new IamRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        permissionService.bumpVersions(userRoleMapper.selectUserIdsByRoleId(roleId));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("menusOnly", menusOnly);
        detail.put("permissionIds", permissionIds);
        auditService.recordQuietly(operatorId, ACTION_ASSIGN_PERMISSION, RESOURCE_ROLE,
                String.valueOf(roleId), true, ip, detail);

        return detail(roleId);
    }

    private void requireAssignablePermissions(List<Long> permissionIds, boolean menusOnly) {
        if (permissionIds.isEmpty()) {
            return;
        }
        List<PermissionBriefRow> rows = permissionMapper.selectActiveByIds(permissionIds);
        if (rows.size() != permissionIds.size()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "权限不存在或已停用");
        }
        Set<Long> submitted = new HashSet<>(permissionIds);
        for (PermissionBriefRow row : rows) {
            if (menusOnly && !"MENU".equals(row.getType())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "菜单分配只能包含 MENU 类型权限");
            }
            if (row.getParentId() != null && !submitted.contains(row.getParentId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "权限树不完整：请同时勾选父级权限");
            }
        }
    }

    private boolean isSuperAdmin(long operatorId) {
        return roleMapper.selectActiveRoleCodesByUserId(operatorId).contains(ROLE_SUPER_ADMIN);
    }

    private AdminRoleRow loadRow(long id) {
        AdminRoleRow row = roleMapper.selectAdminRoleById(id);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "角色不存在");
        }
        return row;
    }

    private RoleView toView(AdminRoleRow row) {
        return new RoleView(
                String.valueOf(row.getId()),
                row.getRoleCode(),
                row.getRoleName(),
                row.getDataScope(),
                Boolean.TRUE.equals(row.getIsSystemRole()),
                row.getStatus(),
                row.getRemark(),
                row.getUserCount() == null ? 0L : row.getUserCount(),
                row.getCreatedAt(),
                row.getVersion() == null ? 0 : row.getVersion());
    }

    private RoleDetailView toDetail(AdminRoleRow row, List<Long> permissionIds) {
        RoleView view = toView(row);
        List<String> ids = new ArrayList<>(permissionIds.size());
        for (Long permissionId : permissionIds) {
            ids.add(String.valueOf(permissionId));
        }
        return new RoleDetailView(view.id(), view.roleCode(), view.roleName(), view.dataScope(),
                view.isSystemRole(), view.status(), view.remark(), view.userCount(),
                view.createdAt(), view.version(), ids);
    }

    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream().filter(id -> id != null).toList()));
    }

    private static String requireOneOf(String value, Set<String> allowed, String message) {
        String text = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
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
}
