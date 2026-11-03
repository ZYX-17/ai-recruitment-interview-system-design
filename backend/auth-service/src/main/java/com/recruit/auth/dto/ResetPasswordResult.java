package com.recruit.auth.dto;

/**
 * 管理员重置密码结果（X-10）。
 *
 * @param temporaryPassword  一次性临时密码明文：仅本次响应返回，服务端只存 BCrypt
 * @param mustChangePassword 是否要求下次登录改密；当前实现固定为 true
 */
public record ResetPasswordResult(String temporaryPassword, boolean mustChangePassword) {
}
