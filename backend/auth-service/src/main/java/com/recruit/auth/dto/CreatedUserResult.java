package com.recruit.auth.dto;

/**
 * 新增用户结果（A-08）。
 *
 * @param id                 新用户 ID（字符串）
 * @param initialPassword    初始密码明文：仅本次响应返回，服务端只存 BCrypt
 * @param mustChangePassword 是否要求首次登录改密
 */
public record CreatedUserResult(String id, String initialPassword, boolean mustChangePassword) {
}
