package com.recruit.gateway.security;

import com.recruit.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 网关侧 Access Token 验签（RS256，docs/4 §2.2/§3.1.2）。
 *
 * <p>只校验签名、iss、exp/nbf 与 {@code typ=access}；黑名单与 token_version
 * 由 {@link TokenStateChecker} 查 Redis。Refresh Token 不允许访问业务接口。</p>
 */
@Component
public class GatewayJwtVerifier {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_VERSION = "tv";
    private static final String CLAIM_TYPE = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String DEFAULT_KEY_ID = "k1";

    private final GatewaySecurityProperties.Jwt properties;
    private final Map<String, PublicKey> publicKeys;
    private final String activeKeyId;

    /**
     * 运行时构造：从配置的 PEM 文件加载公钥，缺失即启动失败。
     *
     * @param properties 网关配置
     */
    @Autowired
    public GatewayJwtVerifier(GatewaySecurityProperties properties) {
        this(properties.getJwt(), loadPublicKeys(properties.getJwt()));
    }

    /**
     * 测试用构造：直接注入内存公钥。
     *
     * @param properties  JWT 配置
     * @param publicKeys  kid -&gt; 公钥
     */
    GatewayJwtVerifier(GatewaySecurityProperties.Jwt properties, Map<String, PublicKey> publicKeys) {
        this.properties = Objects.requireNonNull(properties, "properties");
        if (publicKeys == null || publicKeys.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个验签公钥");
        }
        this.publicKeys = Map.copyOf(publicKeys);
        String keyId = properties.getKeyId();
        this.activeKeyId = keyId == null || keyId.isBlank() ? DEFAULT_KEY_ID : keyId;
        if (!this.publicKeys.containsKey(this.activeKeyId)) {
            throw new IllegalArgumentException("缺少 kid=" + this.activeKeyId + " 对应的公钥");
        }
    }

    /**
     * 校验并解析 Access Token。
     *
     * @param token 紧凑 JWT
     * @return 用户快照
     * @throws JwtException             签名、iss、exp/nbf 校验失败
     * @throws IllegalArgumentException 令牌为空、类型不是 access 或缺少 uid
     */
    public GatewayUser verifyAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("令牌为空");
        }
        Claims claims = Jwts.parser()
                .keyLocator(this::locateKey)
                .requireIssuer(properties.getIssuer())
                .clockSkewSeconds(properties.getClockSkewSeconds())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!TOKEN_TYPE_ACCESS.equals(type)) {
            throw new IllegalArgumentException("令牌类型不是 access");
        }

        String userId = claims.get(CLAIM_USER_ID, String.class);
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("令牌缺少 uid");
        }
        List<?> rawRoles = claims.get(CLAIM_ROLES, List.class);
        List<String> roles = rawRoles == null
                ? List.of()
                : rawRoles.stream().map(String::valueOf).toList();
        Number version = claims.get(CLAIM_TOKEN_VERSION, Number.class);
        return new GatewayUser(userId, roles, claims.getId(),
                version == null ? 0L : version.longValue());
    }

    private Key locateKey(Header header) {
        String kid = header instanceof JwsHeader jwsHeader ? jwsHeader.getKeyId() : null;
        String resolved = kid == null || kid.isBlank() ? activeKeyId : kid;
        PublicKey publicKey = publicKeys.get(resolved);
        if (publicKey == null) {
            throw new JwtException("未知的 kid: " + resolved);
        }
        return publicKey;
    }

    private static Map<String, PublicKey> loadPublicKeys(GatewaySecurityProperties.Jwt properties) {
        Map<String, PublicKey> keys = new LinkedHashMap<>();
        String keyId = properties.getKeyId();
        String activeKid = keyId == null || keyId.isBlank() ? DEFAULT_KEY_ID : keyId;
        keys.put(activeKid, readPublicKey(activeKid, properties.getPublicKeyPath()));
        properties.getPreviousPublicKeys().forEach((kid, path) -> keys.put(kid, readPublicKey(kid, path)));
        return keys;
    }

    private static PublicKey readPublicKey(String kid, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("kid=" + kid + " 未配置公钥路径（JWT_PUBLIC_KEY_PATH）");
        }
        Path file = Path.of(path);
        try {
            byte[] der = decodePem(Files.readString(file, StandardCharsets.UTF_8));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("读取 JWT 公钥失败: " + file, e);
        }
    }

    private static byte[] decodePem(String pem) {
        String normalized = pem.replace("\r", "")
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("PEM 内容为空");
        }
        return Base64.getDecoder().decode(normalized);
    }
}
