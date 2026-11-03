package com.recruit.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理视图（A-07 列表 / X-09 详情）。
 *
 * <p>手机号已脱敏，不返回密码哈希、加密手机号明文或令牌版本。</p>
 *
 * @param id         用户 ID（字符串）
 * @param username   登录名
 * @param displayName 显示名
 * @param userType   EMPLOYEE / CANDIDATE
 * @param status     ACTIVE / LOCKED / DISABLED
 * @param phone      脱敏手机号，未配置加密密钥或未填时为 null
 * @param deptId     部门 ID（字符串）
 * @param deptName   部门名
 * @param roles      角色摘要
 * @param createdAt  创建时间
 * @param version    乐观锁版本
 */
public record AdminUserView(
        String id,
        String username,
        String displayName,
        String userType,
        String status,
        String phone,
        String deptId,
        String deptName,
        List<RoleBrief> roles,
        LocalDateTime createdAt,
        int version) {

    public AdminUserView {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
