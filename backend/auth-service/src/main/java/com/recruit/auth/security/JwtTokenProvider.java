package com.recruit.auth.security;

import com.recruit.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 签发与校验（RS256，docs/4 §7.1）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>只做“签名 + 过期 + 类型 + iss”校验；token_version 与黑名单属于 Redis 侧校验，见 P1-5；</li>
 *   <li>header 带 {@code kid}，轮换期用旧公钥继续验签，避免已签发令牌瞬间失效；</li>
 *   <li>Access 与 Refresh 用 {@code typ} 强区分，Refresh 不能直接访问业务接口；</li>
 *   <li>时钟偏移默认 30s，只影响 exp/nbf 判定，不放宽签名校验。</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_VERSION = "tv";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_FAMILY = "fam";
    private static final String DEFAULT_KEY_ID = "k1";

    private final JwtProperties properties;
    private final PrivateKey privateKey;
    private final Map<String, PublicKey> publicKeys;
    private final String activeKeyId;

    /**
     * 生产/运行时构造：从配置的 PEM 文件加载密钥；缺失即启动失败（fail-closed）。
     *
     * @param properties JWT 配置
     */
    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this(properties, loadPrivateKey(properties), loadPublicKeys(properties));
    }

    /**
     * 测试用构造：直接注入内存密钥，避免在仓库里放固定私钥。
     *
     * @param properties JWT 配置
     * @param privateKey 签名私钥
     * @param publicKeys kid -&gt; 公钥
     */
    JwtTokenProvider(JwtProperties properties, PrivateKey privateKey, Map<String, PublicKey> publicKeys) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
        if (publicKeys == null || publicKeys.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个验签公钥");
        }
        this.publicKeys = Map.copyOf(publicKeys);
        this.activeKeyId = resolveActiveKeyId(properties);
        if (!this.publicKeys.containsKey(activeKeyId)) {
            throw new IllegalArgumentException("缺少 kid=" + activeKeyId + " 对应的公钥");
        }
    }

    /**
     * 签发 Access Token。
     *
     * @param userId       用户 ID
     * @param username     登录名
     * @param roles        角色码（不带权限码，避免令牌膨胀）
     * @param tokenVersion 令牌版本
     * @return 紧凑 JWT
     */
    public String createAccessToken(String userId, String username, List<String> roles, long tokenVersion) {
        Instant now = Instant.now();
        return build(userId, username, roles, tokenVersion, TokenType.ACCESS, null,
                now, now.plusSeconds(accessTokenTtlSeconds()));
    }

    /**
     * 签发 Refresh Token。
     *
     * @param userId       用户 ID
     * @param username     登录名
     * @param roles        角色码
     * @param tokenVersion 令牌版本
     * @param familyId     令牌族 ID，重放检测用
     * @return 紧凑 JWT
     */
    public String createRefreshToken(String userId, String username, List<String> roles,
                                     long tokenVersion, String familyId) {
        if (familyId == null || familyId.isBlank()) {
            throw new IllegalArgumentException("Refresh Token 必须携带 familyId");
        }
        Instant now = Instant.now();
        return build(userId, username, roles, tokenVersion, TokenType.REFRESH, familyId,
                now, now.plusSeconds(refreshTokenTtlSeconds()));
    }

    /**
     * 校验并解析 Access Token。
     *
     * @param token 紧凑 JWT
     * @return 解析后的业务字段
     * @throws JwtException             签名、iss、exp/nbf 校验失败
     * @throws IllegalArgumentException 令牌为空或类型不是 access
     */
    public JwtClaims parseAccessToken(String token) {
        return parse(token, TokenType.ACCESS);
    }

    /**
     * 校验并解析 Refresh Token。
     *
     * @param token 紧凑 JWT
     * @return 解析后的业务字段
     * @throws JwtException             签名、iss、exp/nbf 校验失败
     * @throws IllegalArgumentException 令牌为空或类型不是 refresh
     */
    public JwtClaims parseRefreshToken(String token) {
        return parse(token, TokenType.REFRESH);
    }

    /** Access Token 有效期（秒），登录响应 expiresIn 直接用它。 */
    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtlMinutes() * 60L;
    }

    /** Refresh Token 有效期（秒）。 */
    public long refreshTokenTtlSeconds() {
        return properties.getRefreshTokenTtlDays() * 24L * 3600L;
    }

    private JwtClaims parse(String token, TokenType expected) {
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

        TokenType actual = TokenType.from(claims.get(CLAIM_TYPE, String.class));
        if (actual != expected) {
            throw new IllegalArgumentException("令牌类型不匹配：期望 " + expected.value() + "，实际 " + actual.value());
        }

        String userId = claims.get(CLAIM_USER_ID, String.class);
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("令牌缺少 uid");
        }
        Number tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Number.class);
        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        return new JwtClaims(
                userId,
                claims.get(CLAIM_USERNAME, String.class),
                roles,
                tokenVersion == null ? 0L : tokenVersion.longValue(),
                claims.getId(),
                actual,
                claims.get(CLAIM_FAMILY, String.class),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant());
    }

    private String build(String userId, String username, List<String> roles, long tokenVersion,
                         TokenType type, String familyId, Instant issuedAt, Instant expiresAt) {
        JwtBuilder builder = Jwts.builder()
                .header().keyId(activeKeyId).and()
                .issuer(properties.getIssuer())
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, roles == null ? List.of() : List.copyOf(roles))
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_TYPE, type.value())
                .signWith(privateKey, Jwts.SIG.RS256);
        if (familyId != null) {
            builder.claim(CLAIM_FAMILY, familyId);
        }
        return builder.compact();
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

    private static String resolveActiveKeyId(JwtProperties properties) {
        String keyId = properties.getKeyId();
        return keyId == null || keyId.isBlank() ? DEFAULT_KEY_ID : keyId;
    }

    private static PrivateKey loadPrivateKey(JwtProperties properties) {
        String path = properties.getPrivateKeyPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "未配置 recruit.jwt.private-key-path（JWT_PRIVATE_KEY_PATH）；本地可运行 scripts/dev/gen-jwt-keys.sh 生成密钥");
        }
        Path file = Path.of(path);
        try {
            warnIfPermissive(file);
            byte[] der = decodePem(Files.readString(file, StandardCharsets.UTF_8));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("读取 JWT 私钥失败: " + file, e);
        }
    }

    private static Map<String, PublicKey> loadPublicKeys(JwtProperties properties) {
        Map<String, PublicKey> keys = new LinkedHashMap<>();
        String activeKid = resolveActiveKeyId(properties);
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

    /**
     * 私钥权限检查：POSIX 下 group/others 可读时告警。
     *
     * <p>不直接失败是为了兼容 Windows/容器挂载；生产部署应保证 600，告警会出现在启动日志里。</p>
     */
    private static void warnIfPermissive(Path file) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)) {
                log.warn("JWT 私钥权限过宽（group/others 可读）: {}，建议 chmod 600", file);
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 文件系统（如 Windows）跳过权限检查
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
