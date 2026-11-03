package com.recruit.auth.service;

import com.recruit.auth.dto.CreateDeptForm;
import com.recruit.auth.dto.DeptNodeView;
import com.recruit.auth.dto.UpdateDeptForm;
import com.recruit.auth.entity.IamDepartment;
import com.recruit.auth.mapper.IamDepartmentMapper;
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
 * 部门管理（P2-5 扩展）：部门树与 CRUD。
 *
 * <p>关键口径：</p>
 * <ul>
 *   <li>{@code ancestors} 是根哨兵 {@code 0} 起始的祖级路径，新增/移动必须维护，
 *       否则 DEPARTMENT 数据范围按 {@code ancestors LIKE} 查询会漏子树；</li>
 *   <li>同级部门名称不允许重复；父部门必须存在且启用，移动时禁止成环并校验层级上限；</li>
 *   <li>停用有子部门的节点需先处理子部门；删除前校验子部门与归属用户；</li>
 *   <li>写操作全部落审计，审计摘要不含敏感信息。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminDeptService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Set<String> STATUSES = Set.of(STATUS_ACTIVE, STATUS_DISABLED);
    private static final String ROOT_ANCESTORS = "0";
    private static final int MAX_DEPTH = 32;
    private static final int MAX_ANCESTORS_LENGTH = 255;

    private static final String RESOURCE_DEPT = "DEPT";
    private static final String ACTION_CREATE = "DEPT_CREATE";
    private static final String ACTION_UPDATE = "DEPT_UPDATE";
    private static final String ACTION_DELETE = "DEPT_DELETE";

    private final IamDepartmentMapper departmentMapper;
    private final AuditService auditService;

    /**
     * 部门树：返回全部节点（含停用），父节点缺失的孤儿节点提升为根。
     *
     * @return 部门树
     */
    public List<DeptNodeView> tree() {
        return buildTree(departmentMapper.selectAll());
    }

    /**
     * 新增部门。
     *
     * @param form       新增入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 新部门节点
     */
    @Transactional
    public DeptNodeView create(CreateDeptForm form, long operatorId, String ip) {
        String deptName = form.getDeptName().trim();
        IamDepartment parent = validateParent(form.getParentId(), null);
        if (departmentMapper.countByDeptNameAndParentId(deptName, form.getParentId()) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同级部门名称已存在");
        }
        String status = form.getStatus() == null
                ? STATUS_ACTIVE
                : requireOneOf(form.getStatus(), STATUSES, "部门状态非法");
        String ancestors = ancestorsOf(parent);
        if (ancestors.length() > MAX_ANCESTORS_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门层级过深");
        }

        IamDepartment dept = new IamDepartment();
        dept.setParentId(form.getParentId());
        dept.setAncestors(ancestors);
        dept.setDeptName(deptName);
        dept.setSort(form.getSort() == null ? 0 : form.getSort());
        dept.setStatus(status);
        dept.setVersion(0);
        departmentMapper.insert(dept);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("parentId", form.getParentId());
        detail.put("ancestors", ancestors);
        auditService.recordQuietly(operatorId, ACTION_CREATE, RESOURCE_DEPT,
                String.valueOf(dept.getId()), true, ip, detail);

        return toNode(dept, Map.of());
    }

    /**
     * 修改部门：白名单字段 + 乐观锁；移动时同步重写子孙 {@code ancestors}。
     *
     * @param id         部门 ID
     * @param form       修改入参
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     * @return 更新后的部门节点
     */
    @Transactional
    public DeptNodeView update(long id, UpdateDeptForm form, long operatorId, String ip) {
        IamDepartment current = requireDept(id);
        String deptName = hasText(form.getDeptName()) ? form.getDeptName().trim() : current.getDeptName();
        IamDepartment parent = validateParent(form.getParentId(), id);
        if (departmentMapper.countByDeptNameAndParentIdExcludingId(deptName, form.getParentId(), id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "同级部门名称已存在");
        }

        String status = form.getStatus() == null
                ? current.getStatus()
                : requireOneOf(form.getStatus(), STATUSES, "部门状态非法");
        if (STATUS_DISABLED.equals(status) && STATUS_ACTIVE.equals(current.getStatus())
                && departmentMapper.countByParentId(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先停用或移走子部门");
        }

        String oldAncestors = hasText(current.getAncestors()) ? current.getAncestors() : ROOT_ANCESTORS;
        String newAncestors = ancestorsOf(parent);
        boolean moved = !oldAncestors.equals(newAncestors);
        if (moved) {
            int currentLength = oldAncestors.length();
            int maxLength = departmentMapper.selectMaxAncestorsLength(id);
            int newMaxLength = newAncestors.length() + Math.max(0, maxLength - currentLength);
            if (newMaxLength > MAX_ANCESTORS_LENGTH) {
                throw new BizException(ErrorCode.PARAM_INVALID, "部门层级过深");
            }
        }

        int sort = form.getSort() == null
                ? (current.getSort() == null ? 0 : current.getSort())
                : form.getSort();
        int updated = departmentMapper.updateDept(id, deptName, form.getParentId(), newAncestors,
                sort, status, form.getVersion());
        if (updated == 0) {
            if (departmentMapper.selectById(id) == null) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
            }
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        if (moved) {
            departmentMapper.updateDescendantAncestors(id, oldAncestors, newAncestors);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("parentId", form.getParentId());
        detail.put("status", status);
        detail.put("moved", moved);
        auditService.recordQuietly(operatorId, ACTION_UPDATE, RESOURCE_DEPT,
                String.valueOf(id), true, ip, detail);

        return toNode(requireDept(id), Map.of());
    }

    /**
     * 删除部门：有子部门或仍有用户归属时拒绝（返回 400）。
     *
     * @param id         部门 ID
     * @param operatorId 当前操作者 ID
     * @param ip         客户端 IP，写审计用
     */
    @Transactional
    public void delete(long id, long operatorId, String ip) {
        IamDepartment current = requireDept(id);
        if (departmentMapper.countByParentId(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先删除子部门");
        }
        if (departmentMapper.countUsersByDeptId(id) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仍有用户归属该部门，请先调整用户部门");
        }

        departmentMapper.deleteById(id);
        auditService.recordQuietly(operatorId, ACTION_DELETE, RESOURCE_DEPT,
                String.valueOf(id), true, ip, Map.of("deptName", current.getDeptName()));
    }

    private IamDepartment validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return null;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父部门不能是自己");
        }
        IamDepartment parent = departmentMapper.selectById(parentId);
        if (parent == null || !STATUS_ACTIVE.equals(parent.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "父部门不存在或已停用");
        }
        if (depthOf(parent) + 1 > MAX_DEPTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门层级不能超过 32 级");
        }
        if (selfId == null) {
            return parent;
        }
        // 防环：从新父级向上回溯，遇到自己说明挂到了自己的子孙下
        Long cursor = parent.getParentId();
        int depth = 0;
        while (cursor != null && depth++ < MAX_DEPTH) {
            if (cursor.equals(selfId)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "父部门不能是自己的子部门");
            }
            IamDepartment node = departmentMapper.selectById(cursor);
            cursor = node == null ? null : node.getParentId();
        }
        return parent;
    }

    private IamDepartment requireDept(long id) {
        IamDepartment dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "部门不存在");
        }
        return dept;
    }

    private static String ancestorsOf(IamDepartment parent) {
        if (parent == null) {
            return ROOT_ANCESTORS;
        }
        String base = hasText(parent.getAncestors()) ? parent.getAncestors() : ROOT_ANCESTORS;
        return base + "," + parent.getId();
    }

    private static int depthOf(IamDepartment dept) {
        if (dept == null || !hasText(dept.getAncestors())) {
            return 1;
        }
        return dept.getAncestors().split(",").length;
    }

    private List<DeptNodeView> buildTree(List<IamDepartment> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (IamDepartment row : rows) {
            ids.add(row.getId());
        }
        Map<Long, List<IamDepartment>> byParent = new LinkedHashMap<>();
        List<IamDepartment> roots = new ArrayList<>();
        for (IamDepartment row : rows) {
            if (row.getParentId() == null || !ids.contains(row.getParentId())) {
                roots.add(row);
            } else {
                byParent.computeIfAbsent(row.getParentId(), key -> new ArrayList<>()).add(row);
            }
        }
        List<DeptNodeView> result = new ArrayList<>(roots.size());
        for (IamDepartment root : roots) {
            result.add(toNode(root, byParent));
        }
        return result;
    }

    private DeptNodeView toNode(IamDepartment row, Map<Long, List<IamDepartment>> byParent) {
        List<DeptNodeView> children = new ArrayList<>();
        for (IamDepartment child : byParent.getOrDefault(row.getId(), List.of())) {
            children.add(toNode(child, byParent));
        }
        return new DeptNodeView(
                String.valueOf(row.getId()),
                row.getParentId() == null ? null : String.valueOf(row.getParentId()),
                row.getDeptName(),
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
