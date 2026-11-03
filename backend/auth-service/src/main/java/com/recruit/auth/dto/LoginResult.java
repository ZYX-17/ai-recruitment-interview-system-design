package com.recruit.auth.dto;

import java.util.List;

/**
 * 登录成功响应（docs/4 §5.2.1）。
 *
 * @param accessToken  Access Token（RS256 JWT）
 * @param tokenType    固定 Bearer
 * @param expiresIn    Access 有效期（秒）
 * @param refreshToken Refresh Token；生产为 null，只走 Set-Cookie
 * @param user         用户摘要
 * @param roles        角色码
 * @param permissions  权限码（登录时按角色现算，P2-6 再加缓存）
 * @param tokenVersion 令牌版本，写入 Access claims
 * @param mustChangePassword 管理员重置密码后为 true，前端应强制跳转改密页
 */
public record LoginResult(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        UserSummary user,
        List<String> roles,
        List<String> permissions,
        long tokenVersion,
        boolean mustChangePassword) {

    public LoginResult {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    /**
     * 去掉响应体里的 Refresh Token（生产模式）。
     *
     * @return 新的响应对象，refreshToken 为 null
     */
    public LoginResult withoutRefreshToken() {
        return new LoginResult(accessToken, tokenType, expiresIn, null, user, roles, permissions,
                tokenVersion, mustChangePassword);
    }
}
