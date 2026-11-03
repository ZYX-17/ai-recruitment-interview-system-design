package com.recruit.interview.service;

import com.recruit.common.context.UserContext;
import com.recruit.interview.dto.AuthzSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 当前用户数据范围解析（docs/4 §7.3）。
 *
 * <p>范围来自 auth-service 的角色配置，只用于列表过滤与详情可见性判断；
 * 每次实时读取，不缓存。DEPARTMENT 范围使用本部门 + 全部子部门 ID。</p>
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final PermissionCacheService permissionCacheService;

    /**
     * 解析当前用户数据范围。
     *
     * @return 数据范围快照
     */
    public DataScope current() {
        long userId = Long.parseLong(UserContext.requireUserId());
        AuthzSnapshot snapshot = permissionCacheService.authz(userId);
        String scope = snapshot.dataScope() == null
                ? "SELF"
                : snapshot.dataScope().trim().toUpperCase(Locale.ROOT);
        Long deptId = parseDeptId(snapshot.deptId());
        List<Long> deptIds = parseDeptIds(snapshot.deptIds());
        if (deptIds.isEmpty() && deptId != null) {
            deptIds = List.of(deptId);
        }
        return new DataScope(scope, deptId, deptIds, userId);
    }

    private static Long parseDeptId(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(deptId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> parseDeptIds(List<String> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        List<Long> parsed = new ArrayList<>(deptIds.size());
        for (String deptId : deptIds) {
            Long value = parseDeptId(deptId);
            if (value != null) {
                parsed.add(value);
            }
        }
        return parsed;
    }

    /**
     * 当前用户数据范围。
     *
     * @param scope   ALL/DEPARTMENT/ASSIGNED/SELF
     * @param deptId  所属部门
     * @param deptIds 本部门及全部子部门
     * @param userId  当前用户
     */
    public record DataScope(String scope, Long deptId, List<Long> deptIds, long userId) {

        public DataScope {
            deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
        }

        /**
         * 是否可查看某个部门的资源。
         *
         * @param resourceDeptId 资源部门
         * @param ownerId        资源负责人
         * @return 可见返回 true
         */
        public boolean canSee(Long resourceDeptId, Long ownerId) {
            return switch (scope) {
                case "ALL" -> true;
                case "DEPARTMENT" -> resourceDeptId != null && deptIds.contains(resourceDeptId);
                default -> ownerId != null && ownerId == userId;
            };
        }

        /**
         * 列表 SQL 需要的范围参数。
         *
         * @return 范围名
         */
        public String sqlScope() {
            return switch (scope) {
                case "ALL", "DEPARTMENT", "ASSIGNED" -> scope;
                default -> "SELF";
            };
        }
    }
}
