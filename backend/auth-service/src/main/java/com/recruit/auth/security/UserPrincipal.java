package com.recruit.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * 认证通过后的当前用户主体。
 *
 * <p>只是 JWT 声明的内存投影：角色码用于 {@code @PreAuthorize("hasRole('HR_ADMIN')")}，
 * 权限码不在令牌里（docs/4 §7.1.1），需要时由服务端缓存读取。</p>
 *
 * <p>安全层不保存密码：{@link #getPassword()} 恒为 {@code null}，
 * 避免误把主体序列化或写日志时带出凭据。</p>
 */
public final class UserPrincipal implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    private final String userId;
    private final String username;
    private final List<String> roles;
    private final long tokenVersion;
    private final String jti;
    private final List<GrantedAuthority> authorities;

    private UserPrincipal(String userId, String username, List<String> roles,
                          long tokenVersion, String jti) {
        this.userId = userId;
        this.username = username;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.tokenVersion = tokenVersion;
        this.jti = jti;
        this.authorities = this.roles.stream()
                .map(role -> role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    /**
     * 从已验签的声明构造主体。
     *
     * @param claims Access Token 声明
     * @return 当前用户主体
     */
    public static UserPrincipal from(JwtClaims claims) {
        return new UserPrincipal(claims.userId(), claims.username(), claims.roles(),
                claims.tokenVersion(), claims.jti());
    }

    /** 用户 ID（JWT 的 {@code uid}）。 */
    public String getUserId() {
        return userId;
    }

    /** 角色码（不含 {@code ROLE_} 前缀）。 */
    public List<String> getRoles() {
        return roles;
    }

    /** 令牌版本，供 P1-5 与 Redis {@code auth:tv:{userId}} 比对。 */
    public long getTokenVersion() {
        return tokenVersion;
    }

    /** 令牌唯一 ID，供黑名单使用。 */
    public String getJti() {
        return jti;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "UserPrincipal{userId='" + userId + "'}";
    }
}
