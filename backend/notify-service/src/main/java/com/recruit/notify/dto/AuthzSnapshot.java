package com.recruit.notify.dto;

import java.util.List;

/**
 * auth-service 返回的用户鉴权快照。
 *
 * @param permissions 生效权限码
 * @param roles       生效角色码
 * @param dataScope   ALL/DEPARTMENT/ASSIGNED/SELF
 * @param deptId      部门 ID
 * @param deptIds     本部门及子部门 ID
 */
public record AuthzSnapshot(
        List<String> permissions,
        List<String> roles,
        String dataScope,
        String deptId,
        List<String> deptIds) {

    public AuthzSnapshot {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        roles = roles == null ? List.of() : List.copyOf(roles);
        deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
    }
}
