package com.recruit.auth.service;

import com.recruit.auth.dto.CreatePermissionForm;
import com.recruit.auth.dto.PermissionNodeView;
import com.recruit.auth.dto.UpdatePermissionForm;
import com.recruit.auth.entity.IamPermission;
import com.recruit.auth.mapper.IamPermissionMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 权限管理（P2-4：A-15 权限树与权限 CRUD）。
 *
 * <p>关键口径：</p>
 * <ul>
 *   <li>一个权限码一行；权限码与类型创建后不可修改，避免角色授权与前端路由对不上；</li>
 *   <li>只有 MENU 可以作为父级；父级移动时向上回溯，禁止成环；</li>
 *   <li>停用或删除前先检查子节点；删除前再检查角色引用，避免产生悬空授权；</li>
 *   <li>权限定义变更后清理全部 {@code auth:perm:*} 缓存，菜单/按钮即时生效。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String TYPE_MENU = "MENU";
    private static final Set<String> TYPES = Set.of(TYPE_MENU, "BUTTON", "API");
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_DISABLED);
    private static final int MAX_PARENT_DEPTH = 32;

    private static final String RESOURCE_PERMISSION = "PERMISSION";
    private static final String ACTION_CREATE = "PERMISSION_CREATE";
    private static final String ACTION_UPDATE = "PERMISSION_UPDATE";
    private static final String ACTION_DELETE = "PERMISSION_DELETE";

    private final IamPermissionMapper permissionMapper;
    private final PermissionService permissionService;
    private final AuditService auditService;

    /**
     * 权限树（A-15）：返回全部节点（含停用），供管理端勾选与维护。
     *
     * @return 权限树
     */
    public List<PermissionNodeView> tree() {
        return buildTree(permissionMapper.selectAll());
    }

    /**
     * 新增权限（A-15）。
     *
     * @param form       新增入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 新权限节点
     */
    @Transactional
    public PermissionNodeView create(CreatePermissionForm form, long operatorId, String ip) {
        String code = form.getPermissionCode().trim();
        if (permissionMapper.selectByCode(code) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "权限码已存在");
        }
        String type = requireOneOf(form.getType(), TYPES, "权限类型非法");
        String status = form.getStatus() == null
                ? STATUS_ACTIVE
                : requireOneOf(form.getStatus(), STATUSES, "权限状态非法");
        validateParent(form.getParentId(), null);

        IamPermission permission = new IamPermission();
        permission.setPermissionCode(code);
        permission.setType(type);
        permission.setParentId(form.getParentId());
        permission.setName(form.getName().trim());
        permission.setRoutePath(trimToNull(form.getRoutePath()));
        permission.setComponent(trimToNull(form.getComponent()));
        permission.setIcon(trimToNull(form.getIcon()));
        permission.setApiPattern(trimToNull(form.getApiPattern()));
        permission.setSort(form.getSort() == null ? 0 : form.getSort());
        permission.setStatus(status);
        permission.setVersion(0);
        permissionMapper.insert(permission);

        permissionService.evictAllPermissionCaches();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", code);
        detail.put("type", type);
        detail.put("parentId", form.getParentId());
        auditService.recordQuietly(operatorId, ACTION_CREATE, RESOURCE_PERMISSION,
                String.valueOf(permission.getId()), true, ip, detail);

        return toNode(permission, Map.of());
    }

    /**
     * 修改权限（A-15）。
     *
     * @param id         权限 ID
     * @param form       修改入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 更新后的权限节点
     */
    @Transactional
    public PermissionNodeView update(long id, UpdatePermissionForm form, long operatorId, String ip) {
        IamPermission current = requirePermission(id);
        String name = hasText(form.getName()) ? form.getName().trim() : current.getName();
        String status = form.getStatus() == null
                ? current.getStatus()
                : requireOneOf(form.getStatus(), STATUSES, "权限状态非法");
        validateParent(form.getParentId(), id);

        if (STATUS_DISABLED.equals(status) && STATUS_ACTIVE.equals(current.getStatus())
                && permissionMapper.countByParentId(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先停用或移除子权限");
        }

        int sort = form.getSort() == null
                ? (current.getSort() == null ? 0 : current.getSort())
                : form.getSort();
        int updated = permissionMapper.updatePermission(id, name, form.getParentId(),
                trimToNull(form.getRoutePath()), trimToNull(form.getComponent()),
                trimToNull(form.getIcon()), trimToNull(form.getApiPattern()),
                sort, status, form.getVersion());
        if (updated == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        permissionService.evictAllPermissionCaches();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("status", status);
        detail.put("parentId", form.getParentId());
        auditService.recordQuietly(operatorId, ACTION_UPDATE, RESOURCE_PERMISSION,
                String.valueOf(id), true, ip, detail);

        return toNode(requirePermission(id), Map.of());
    }

    /**
     * 删除权限（A-15）。
     *
     * <p>有子节点或仍被角色引用时拒绝删除，避免菜单树断裂或角色授权指向不存在的权限。</p>
     *
     * @param id         权限 ID
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     */
    @Transactional
    public void delete(long id, long operatorId, String ip) {
        IamPermission current = requirePermission(id);
        if (permissionMapper.countByParentId(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先删除子权限");
        }
        if (permissionMapper.countRoleReferences(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仍有角色引用该权限，请先取消授权");
        }

        permissionMapper.deleteById(id);
        permissionService.evictAllPermissionCaches();

        auditService.recordQuietly(operatorId, ACTION_DELETE, RESOURCE_PERMISSION,
                String.valueOf(id), true, ip, Map.of("code", current.getPermissionCode()));
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父级不能是自己");
        }
        IamPermission parent = permissionMapper.selectById(parentId);
        if (parent == null || !STATUS_ACTIVE.equals(parent.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父级权限不存在或已停用");
        }
        if (!TYPE_MENU.equals(parent.getType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父级权限必须是 MENU");
        }
        if (selfId == null) {
            return;
        }
        // 防环：从新父级往上走，遇到自己说明把节点挂到了自己的子孙下
        Long cursor = parent.getParentId();
        int depth = 0;
        while (cursor != null && depth++ < MAX_PARENT_DEPTH) {
            if (cursor.equals(selfId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "父级不能是自己的子节点");
            }
            IamPermission node = permissionMapper.selectById(cursor);
            cursor = node == null ? null : node.getParentId();
        }
    }

    private IamPermission requirePermission(long id) {
        IamPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "权限不存在");
        }
        return permission;
    }

    private List<PermissionNodeView> buildTree(List<IamPermission> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (IamPermission row : rows) {
            ids.add(row.getId());
        }
        Map<Long, List<IamPermission>> byParent = new LinkedHashMap<>();
        List<IamPermission> roots = new ArrayList<>();
        for (IamPermission row : rows) {
            if (row.getParentId() == null || !ids.contains(row.getParentId())) {
                roots.add(row);
            } else {
                byParent.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(row);
            }
        }
        List<PermissionNodeView> result = new ArrayList<>(roots.size());
        for (IamPermission root : roots) {
            result.add(toNode(root, byParent));
        }
        return result;
    }

    private PermissionNodeView toNode(IamPermission row, Map<Long, List<IamPermission>> byParent) {
        List<PermissionNodeView> children = new ArrayList<>();
        for (IamPermission child : byParent.getOrDefault(row.getId(), List.of())) {
            children.add(toNode(child, byParent));
        }
        return new PermissionNodeView(
                String.valueOf(row.getId()),
                row.getParentId() == null ? null : String.valueOf(row.getParentId()),
                row.getPermissionCode(),
                row.getName(),
                row.getType(),
                row.getRoutePath(),
                row.getComponent(),
                row.getIcon(),
                row.getApiPattern(),
                row.getSort() == null ? 0 : row.getSort(),
                row.getStatus(),
                row.getVersion() == null ? 0 : row.getVersion(),
                children);
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
