package com.recruit.auth.dto;

import java.util.List;

/**
 * 当前登录用户视图（A-05，{@code GET /api/auth/me}）。
 *
 * <p>手机号只返回脱敏值；权限码与数据范围按角色实时解析，不从 JWT 读取。</p>
 *
 * @param userId             用户 ID
 * @param username           登录名
 * @param displayName        显示姓名
 * @param userType           EMPLOYEE / CANDIDATE
 * @param phoneMasked        脱敏手机号，未配置或未填写时为 null
 * @param deptId             所属部门 ID，无部门时为 null
 * @param deptIds            本部门及全部子部门 ID
 * @param roles              生效角色码
 * @param permissions        生效权限码
 * @param dataScope          ALL/DEPARTMENT/ASSIGNED/SELF
 * @param mustChangePassword 是否要求先改密
 */
public record MeView(
        String userId,
        String username,
        String displayName,
        String userType,
        String phoneMasked,
        String deptId,
        List<String> deptIds,
        List<String> roles,
        List<String> permissions,
        String dataScope,
        boolean mustChangePassword) {

    public MeView {
        deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
