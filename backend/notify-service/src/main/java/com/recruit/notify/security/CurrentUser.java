package com.recruit.notify.security;

import java.util.List;

/**
 * 网关注入的当前用户。
 *
 * @param userId 用户 ID
 * @param roles  角色码
 */
public record CurrentUser(String userId, List<String> roles) {

    public CurrentUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
