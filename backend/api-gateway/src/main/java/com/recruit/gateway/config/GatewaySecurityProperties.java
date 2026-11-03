package com.recruit.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关鉴权与限流配置（docs/4 §3.1.2）。
 *
 * <p>公钥从环境变量/Nacos 注入，仓库里不放密钥；公钥缺失时启动失败，
 * 避免网关以“无法验签”的状态接受流量。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "recruit.gateway")
public class GatewaySecurityProperties {

    private final Jwt jwt = new Jwt();

    private final RateLimit rateLimit = new RateLimit();

    /**
     * JWT 验签配置。
     */
    @Getter
    @Setter
    public static class Jwt {

        /** 签发方，必须与 auth-service 的 {@code iss} 一致。 */
        private String issuer = "recruit-auth";

        /** 当前公钥 kid，与 JWT header 的 {@code kid} 对应。 */
        private String keyId = "k1";

        /** 当前公钥 PEM 路径（JWT_PUBLIC_KEY_PATH）。 */
        private String publicKeyPath;

        /** 轮换期旧公钥：kid -> PEM 路径。 */
        private Map<String, String> previousPublicKeys = new LinkedHashMap<>();

        /** exp/nbf 容忍秒数，不放宽签名校验。 */
        private long clockSkewSeconds = 30;
    }

    /**
     * 限流阈值（docs/4 §10.3：全局 200 QPS、单 IP 50 QPS、登录 100/min）。
     */
    @Getter
    @Setter
    public static class RateLimit {

        private int globalQps = 200;

        private int ipQps = 50;

        private int loginPerMinute = 100;
    }
}
