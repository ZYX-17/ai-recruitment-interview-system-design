package com.recruit.auth.support;

import com.recruit.auth.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 构造 Refresh Token Cookie。
 *
 * <p>{@code HttpOnly + SameSite=Strict + Path=/api/auth}：XSS 读不到，
 * 跨站请求不会自动携带，业务接口也拿不到。local 关闭 {@code Secure} 才能用 HTTP 联调。</p>
 */
@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    private final SecurityProperties properties;

    /**
     * 生成写 Cookie 头。
     *
     * @param token      Refresh Token
     * @param ttlSeconds 有效期（秒），与 JWT exp 保持一致
     * @return Set-Cookie 值
     */
    public ResponseCookie create(String token, long ttlSeconds) {
        return base(token)
                .maxAge(Duration.ofSeconds(ttlSeconds))
                .build();
    }

    /**
     * 生成清除 Cookie 头（刷新失败/重放时下发，避免前端反复带坏 token）。
     *
     * @return Set-Cookie 值
     */
    public ResponseCookie clear() {
        return base("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        SecurityProperties.RefreshCookie cookie = properties.getRefreshCookie();
        return ResponseCookie.from(cookie.getName(), value)
                .httpOnly(cookie.isHttpOnly())
                .secure(cookie.isSecure())
                .sameSite(cookie.getSameSite())
                .path(cookie.getPath());
    }
}
