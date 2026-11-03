package com.recruit.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色详情视图（X-09），在 {@link RoleView} 基础上带已勾选的权限 ID。
 *
 * @param permissionIds 已分配的权限 ID（字符串，按 ID 升序）
 */
public record RoleDetailView(
        String id,
        String roleCode,
        String roleName,
        String dataScope,
        boolean isSystemRole,
        String status,
        String remark,
        long userCount,
        LocalDateTime createdAt,
        int version,
        List<String> permissionIds) {

    public RoleDetailView {
        permissionIds = permissionIds == null ? List.of() : List.copyOf(permissionIds);
    }
}
