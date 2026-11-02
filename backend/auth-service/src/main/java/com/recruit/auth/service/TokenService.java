package com.recruit.auth.service;

import com.recruit.auth.entity.IamUser;
import com.recruit.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 令牌签发入口：把用户实体转成 JWT 声明，避免 Controller/Service 直接拼 claim。
 *
 * <p>Access 与 Refresh 都携带角色码和 {@code tv}；Refresh 额外带 {@code fam}。
 * 权限码不进令牌（docs/4 §7.1.1）。</p>
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 签发 Access Token。
     *
     * @param user         用户
     * @param roles        角色码
     * @param tokenVersion 令牌版本
     * @return JWT
     */
    public String createAccessToken(IamUser user, List<String> roles, long tokenVersion) {
        return jwtTokenProvider.createAccessToken(
                String.valueOf(user.getId()), user.getUsername(), roles, tokenVersion);
    }

    /**
     * 签发 Refresh Token。
     *
     * @param user         用户
     * @param roles        角色码
     * @param tokenVersion 令牌版本
     * @param familyId     令牌族 ID
     * @return JWT
     */
    public String createRefreshToken(IamUser user, List<String> roles, long tokenVersion, String familyId) {
        return jwtTokenProvider.createRefreshToken(
                String.valueOf(user.getId()), user.getUsername(), roles, tokenVersion, familyId);
    }

    /** Access 有效期（秒）。 */
    public long accessTtlSeconds() {
        return jwtTokenProvider.accessTokenTtlSeconds();
    }

    /** Refresh 有效期（秒）。 */
    public long refreshTtlSeconds() {
        return jwtTokenProvider.refreshTokenTtlSeconds();
    }

    /**
     * 校验并解析 Refresh Token（供 P1-4 轮换使用）。
     *
     * @param token Refresh Token
     * @return 声明
     */
    public com.recruit.auth.security.JwtClaims parseRefreshToken(String token) {
        return jwtTokenProvider.parseRefreshToken(token);
    }
}
