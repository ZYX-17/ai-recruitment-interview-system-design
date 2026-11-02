package com.recruit.auth.dto;

/**
 * 登录响应中的用户摘要（不返回密码、手机号明文）。
 *
 * @param id          用户 ID（字符串输出）
 * @param username    登录名
 * @param displayName 显示名
 * @param userType    EMPLOYEE / CANDIDATE
 * @param avatar      头像地址，本期为空
 * @param deptId      部门 ID
 * @param deptName    部门名
 */
public record UserSummary(
        String id,
        String username,
        String displayName,
        String userType,
        String avatar,
        String deptId,
        String deptName) {
}
