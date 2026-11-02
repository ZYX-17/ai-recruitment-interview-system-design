package com.recruit.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JWT 配置（对应 docs/4 §3.2.3）。
 *
 * <p>密钥路径只放路径，不放内容：私钥仅 auth-service 可读，公钥分发给网关与其他服务；
 * 文件本身被 .gitignore 的 {@code *.pem} 规则排除，禁止入库。</p>
 */
@Data
@ConfigurationProperties(prefix = "recruit.jwt")
public class JwtProperties {

    /** 签发者，校验时要求 iss 完全一致。 */
    private String issuer = "recruit-auth";

    /** Access Token 有效期（分钟），文档基线 120。 */
    private long accessTokenTtlMinutes = 120;

    /** Refresh Token 有效期（天），文档基线 7。 */
    private long refreshTokenTtlDays = 7;

    /** PKCS#8 私钥文件路径；为空时启动失败（fail-closed）。 */
    private String privateKeyPath;

    /** X.509 公钥文件路径。 */
    private String publicKeyPath;

    /** 时钟偏移容忍（秒），默认 30；用于 exp/nbf 校验。 */
    private long clockSkewSeconds = 30;

    /** 当前签名使用的 kid；写进 JWT header，便于轮换时多公钥并存。 */
    private String keyId = "k1";

    /**
     * 旧公钥（kid -&gt; 文件路径），用于密钥轮换期验签。
     *
     * <p>轮换顺序：新私钥 + 新 kid 开始签发，旧公钥保留至少一个 Access TTL，
     * 到期后从本映射移除。</p>
     */
    private Map<String, String> previousPublicKeys = new LinkedHashMap<>();
}
