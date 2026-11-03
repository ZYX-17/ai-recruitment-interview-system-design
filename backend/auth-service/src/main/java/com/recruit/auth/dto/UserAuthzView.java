package com.recruit.auth.dto;

import java.util.List;

/**
 * 服务间鉴权快照：权限码、角色、数据范围与部门。
 *
 * <p>只经 {@code /internal/users/{id}/authz} 返回，供 interview/notify 做
 * {@code @PreAuthorize} 与数据范围判断；不包含手机号、密码等敏感字段。</p>
 *
 * @param permissions 生效权限码
 * @param roles       生效角色码
 * @param dataScope   ALL/DEPARTMENT/ASSIGNED/SELF
 * @param deptId      用户所属部门 ID（无部门时为空）
 * @param deptIds     本部门及全部子部门 ID（DEPARTMENT 范围使用；无部门时为空）
 */
public record UserAuthzView(
        List<String> permissions,
        List<String> roles,
        String dataScope,
        String deptId,
        List<String> deptIds) {

    public UserAuthzView {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        roles = roles == null ? List.of() : List.copyOf(roles);
        deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
    }
}
