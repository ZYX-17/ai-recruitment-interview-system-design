package com.recruit.auth.support;

import com.recruit.auth.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RefreshCookieFactory} 的单元测试。
 */
class RefreshCookieFactoryTest {

    @Test
    void cookieCarriesSecurityAttributes() {
        SecurityProperties properties = new SecurityProperties();
        RefreshCookieFactory factory = new RefreshCookieFactory(properties);

        ResponseCookie cookie = factory.create("refresh-token", 604800);

        assertEquals("recruit_rt", cookie.getName());
        assertEquals("refresh-token", cookie.getValue());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals("/api/auth", cookie.getPath());
        assertEquals(Duration.ofSeconds(604800), cookie.getMaxAge());
    }
}
