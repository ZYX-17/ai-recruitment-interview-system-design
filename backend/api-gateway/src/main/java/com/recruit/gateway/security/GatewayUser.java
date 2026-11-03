package com.recruit.gateway.security;

import java.util.List;

/**
 * 网关注入下游的用户快照。
 *
 * @param userId       用户 ID（X-User-Uid）
 * @param roles        角色码（X-User-Role，逗号分隔）
 * @param jti          Access Token 唯一 ID，用于黑名单
 * @param tokenVersion 令牌版本，用于与 Redis {@code auth:tv:{uid}} 比对
 */
public record GatewayUser(String userId, List<String> roles, String jti, long tokenVersion) {

    public GatewayUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
