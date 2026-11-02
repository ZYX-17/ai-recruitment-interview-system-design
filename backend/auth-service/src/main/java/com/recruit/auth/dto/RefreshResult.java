package com.recruit.auth.dto;

/**
 * 刷新成功响应（docs/4 §5.2.2）。
 *
 * @param accessToken  新 Access Token
 * @param tokenType    固定 Bearer
 * @param expiresIn    Access 有效期（秒）
 * @param refreshToken 轮换后的 Refresh Token；生产为 null，只走 Set-Cookie
 */
public record RefreshResult(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken) {

    /**
     * 去掉响应体里的 Refresh Token（生产模式）。
     *
     * @return 新对象，refreshToken 为 null
     */
    public RefreshResult withoutRefreshToken() {
        return new RefreshResult(accessToken, tokenType, expiresIn, null);
    }
}
