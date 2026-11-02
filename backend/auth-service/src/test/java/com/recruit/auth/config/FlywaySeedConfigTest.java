package com.recruit.auth.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FlywaySeedConfig} 的密码占位符测试：只验证 BCrypt 注入与 fail-fast，不连接数据库。
 */
class FlywaySeedConfigTest {

    private final FlywaySeedConfig config = new FlywaySeedConfig();

    @Test
    void injectsBcryptHashForSeedAdminPassword() {
        FluentConfiguration flyway = org.flywaydb.core.Flyway.configure();

        config.seedAdminPasswordCustomizer("Admin@12345").customize(flyway);

        String hash = flyway.getPlaceholders().get(FlywaySeedConfig.SEED_ADMIN_PASSWORD_HASH);
        assertNotNull(hash);
        assertTrue(new BCryptPasswordEncoder().matches("Admin@12345", hash));
    }

    @Test
    void blankPasswordFailsFast() {
        assertThrows(IllegalStateException.class, () -> config.seedAdminPasswordCustomizer("  "));
    }

    @Test
    void shortPasswordFailsFast() {
        assertThrows(IllegalStateException.class, () -> config.seedAdminPasswordCustomizer("short"));
    }
}
