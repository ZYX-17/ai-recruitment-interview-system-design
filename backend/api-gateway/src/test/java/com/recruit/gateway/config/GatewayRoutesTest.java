package com.recruit.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由与 CORS 契约（P3-3/P3-5）：直接校验打包进产物的 {@code application.yml}。
 */
class GatewayRoutesTest {

    private static Map<String, Object> gateway;
    private static Map<String, Object> root;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadConfig() {
        try (InputStream input = GatewayRoutesTest.class.getResourceAsStream("/application.yml")) {
            root = new Yaml().load(input);
        } catch (Exception e) {
            throw new IllegalStateException("读取 application.yml 失败", e);
        }
        Map<String, Object> spring = (Map<String, Object>) root.get("spring");
        Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
        gateway = (Map<String, Object>) cloud.get("gateway");
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminRoutesExistAndOutboxPrecedesAdmin() {
        List<Map<String, Object>> routes = (List<Map<String, Object>>) gateway.get("routes");
        List<String> ids = routes.stream().map(route -> String.valueOf(route.get("id"))).toList();

        assertTrue(ids.contains("auth-admin"));
        assertTrue(ids.contains("auth-outbox"));
        assertTrue(ids.contains("interview-outbox"));
        assertTrue(ids.contains("notify-outbox"));
        assertTrue(ids.indexOf("auth-outbox") < ids.indexOf("auth-admin"));
        assertTrue(ids.indexOf("interview-outbox") < ids.indexOf("auth-admin"));
        assertTrue(ids.indexOf("notify-outbox") < ids.indexOf("auth-admin"));
        assertTrue(ids.indexOf("auth-admin") < ids.indexOf("auth-service"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminRouteTargetsAuthServiceAndCoversAdminPath() {
        List<Map<String, Object>> routes = (List<Map<String, Object>>) gateway.get("routes");
        Map<String, Object> admin = routes.stream()
                .filter(route -> "auth-admin".equals(route.get("id")))
                .findFirst()
                .orElseThrow();

        assertEquals("lb://auth-service", admin.get("uri"));
        assertTrue(String.valueOf(admin.get("predicates")).contains("/api/admin/**"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void corsDoesNotUseWildcardWithCredentials() {
        Map<String, Object> globalcors = (Map<String, Object>) gateway.get("globalcors");
        Map<String, Object> configurations = (Map<String, Object>) globalcors.get("cors-configurations");
        Map<String, Object> any = (Map<String, Object>) configurations.get("[/**]");

        String origins = String.valueOf(any.get("allowedOrigins"));
        assertFalse(origins.contains("*"));
        assertTrue(origins.contains("CORS_ALLOWED_ORIGINS"));
        assertEquals(Boolean.TRUE, any.get("allowCredentials"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gatewaySecurityConfigIsPresent() {
        Map<String, Object> recruit = (Map<String, Object>) root.get("recruit");
        Map<String, Object> gatewayConfig = (Map<String, Object>) recruit.get("gateway");
        Map<String, Object> jwt = (Map<String, Object>) gatewayConfig.get("jwt");
        Map<String, Object> rateLimit = (Map<String, Object>) gatewayConfig.get("rate-limit");

        assertTrue(String.valueOf(jwt.get("public-key-path")).contains("JWT_PUBLIC_KEY_PATH"));
        assertEquals(200, rateLimit.get("global-qps"));
        assertEquals(50, rateLimit.get("ip-qps"));
        assertEquals(100, rateLimit.get("login-per-minute"));
    }
}
