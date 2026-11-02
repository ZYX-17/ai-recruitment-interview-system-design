package com.recruit.auth.security;

import java.time.Instant;
import java.util.List;

/**
 * 解析后的 JWT 语义（只保留业务关心的字段）。
 *
 * @param userId       用户 ID（sub/uid）
 * @param username     登录名（uname）
 * @param roles        角色码列表；权限码不入令牌（docs/4 §7.1.1）
 * @param tokenVersion 令牌版本（tv），与 Redis {@code auth:tv:{userId}} 比较
 * @param jti          令牌唯一 ID，用于黑名单与 Refresh 重放检测
 * @param type         ACCESS / REFRESH
 * @param familyId     Refresh 所属令牌族；Access 为 null
 * @param issuedAt     签发时间
 * @param expiresAt    过期时间
 */
public record JwtClaims(
        String userId,
        String username,
        List<String> roles,
        long tokenVersion,
        String jti,
        TokenType type,
        String familyId,
        Instant issuedAt,
        Instant expiresAt) {

    public JwtClaims {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
