package com.recruit.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证安全配置（docs/4 §3.2.3 的 {@code recruit.security.*}）。
 *
 * <p>Refresh Cookie 只存 HttpOnly，脚本不可读；{@code exposeInBody} 仅 local
 * 调试用，生产必须保持 false。</p>
 */
@Data
@ConfigurationProperties(prefix = "recruit.security")
public class SecurityProperties {

    private RefreshCookie refreshCookie = new RefreshCookie();
    private Login login = new Login();

    /**
     * Refresh Token Cookie 属性。
     */
    @Data
    public static class RefreshCookie {

        /** Cookie 名，前端不需要读取。 */
        private String name = "recruit_rt";

        private boolean httpOnly = true;

        /** 生产必须 true（HTTPS）；local 用 application-local.yml 关闭。 */
        private boolean secure = true;

        private String sameSite = "Strict";

        /** 只发给 auth 接口，业务接口拿不到 Refresh Token。 */
        private String path = "/api/auth";

        /** 仅 local：响应体同时返回 refreshToken，便于联调。 */
        private boolean exposeInBody = false;
    }

    /**
     * 登录失败锁定口径。
     */
    @Data
    public static class Login {

        /** 连续失败次数阈值。 */
        private int maxFailures = 5;

        /** 锁定时长（分钟），也是失败计数的 Redis TTL。 */
        private long lockMinutes = 15;
    }
}
