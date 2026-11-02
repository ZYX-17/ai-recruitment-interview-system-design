package com.recruit.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * Flyway 种子占位符：把 {@code SEED_ADMIN_PASSWORD} 转成 BCrypt 后注入 V1_1。
 *
 * <p>密码只从环境变量读取，不写入 SQL、yml、日志或镜像；数据库里只落 BCrypt 哈希。
 * 未配置或过短时启动失败，避免用空密码初始化管理员。</p>
 *
 * <p>CI 的空库迁移走 Flyway Maven 插件，不经过 Spring 容器，因此由
 * {@code scripts/ci/flyway-empty-db.sh} 传入固定测试哈希。</p>
 */
@Configuration(proxyBeanMethods = false)
public class FlywaySeedConfig {

    /** V1_1__seed.sql 中使用的占位符名。 */
    public static final String SEED_ADMIN_PASSWORD_HASH = "seedAdminPasswordHash";

    /**
     * 构造 Flyway 自定义器。
     *
     * @param seedAdminPassword 环境变量 SEED_ADMIN_PASSWORD
     * @return 注入 seedAdminPasswordHash 的自定义器
     */
    @Bean
    public FlywayConfigurationCustomizer seedAdminPasswordCustomizer(
            @Value("${SEED_ADMIN_PASSWORD:}") String seedAdminPassword) {
        String password = seedAdminPassword == null ? "" : seedAdminPassword.trim();
        if (password.isEmpty()) {
            throw new IllegalStateException("未配置 SEED_ADMIN_PASSWORD，拒绝以空密码初始化管理员");
        }
        if (password.length() < 8) {
            throw new IllegalStateException("SEED_ADMIN_PASSWORD 至少 8 位");
        }

        String hash = new BCryptPasswordEncoder(10).encode(password);
        return configuration -> {
            Map<String, String> placeholders = new HashMap<>(configuration.getPlaceholders());
            placeholders.put(SEED_ADMIN_PASSWORD_HASH, hash);
            configuration.placeholders(placeholders);
        };
    }
}
