package com.recruit.auth.dto;

import java.time.LocalDateTime;

/**
 * 角色列表/详情视图（A-12 / X-09）。
 *
 * @param id           角色 ID（字符串）
 * @param roleCode     角色编码
 * @param roleName     角色名
 * @param dataScope    数据范围 ALL/DEPARTMENT/ASSIGNED/SELF
 * @param isSystemRole 是否系统内置角色
 * @param status       ACTIVE / DISABLED
 * @param remark       备注
 * @param userCount    绑定用户数
 * @param createdAt    创建时间
 * @param version      乐观锁版本
 */
public record RoleView(
        String id,
        String roleCode,
        String roleName,
        String dataScope,
        boolean isSystemRole,
        String status,
        String remark,
        long userCount,
        LocalDateTime createdAt,
        int version) {
}
