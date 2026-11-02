package com.recruit.auth.security;

import com.recruit.auth.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtTokenProvider} 的单元测试。
 *
 * <p>密钥在内存里生成，不依赖 PEM 文件，测试可直接在 CI 跑；覆盖 docs/4 §7.1
 * 的声明、类型隔离、时钟偏移与 kid 轮换口径。</p>
 */
class JwtTokenProviderTest {

    private static KeyPair currentKeys;
    private static KeyPair rotatedOldKeys;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        currentKeys = generator.generateKeyPair();
        rotatedOldKeys = generator.generateKeyPair();
    }

    @Test
    void accessTokenCarriesDocumentedClaims() {
        JwtTokenProvider provider = provider("k1", 120, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));

        String token = provider.createAccessToken("10001", "hr01", List.of("RECRUITER", "INTERVIEWER"), 3L);
        JwtClaims claims = provider.parseAccessToken(token);

        assertEquals("10001", claims.userId());
        assertEquals("hr01", claims.username());
        assertEquals(List.of("RECRUITER", "INTERVIEWER"), claims.roles());
        assertEquals(3L, claims.tokenVersion());
        assertEquals(TokenType.ACCESS, claims.type());
        assertNull(claims.familyId());
        assertNotNull(claims.jti());
        assertEquals(provider.accessTokenTtlSeconds(),
                claims.expiresAt().getEpochSecond() - claims.issuedAt().getEpochSecond());
        assertEquals("k1", Jwts.parser().verifyWith(currentKeys.getPublic()).build()
                .parseSignedClaims(token).getHeader().getKeyId());
    }

    @Test
    void refreshTokenRequiresAndCarriesFamily() {
        JwtTokenProvider provider = provider("k1", 120, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));

        String token = provider.createRefreshToken("10001", "hr01", List.of("RECRUITER"), 3L, "family-1");
        JwtClaims claims = provider.parseRefreshToken(token);

        assertEquals(TokenType.REFRESH, claims.type());
        assertEquals("family-1", claims.familyId());
        assertEquals(provider.refreshTokenTtlSeconds(),
                claims.expiresAt().getEpochSecond() - claims.issuedAt().getEpochSecond());
        assertThrows(IllegalArgumentException.class,
                () -> provider.createRefreshToken("10001", "hr01", List.of("RECRUITER"), 3L, " "));
    }

    @Test
    void tokenTypeIsStrictlyMatched() {
        JwtTokenProvider provider = provider("k1", 120, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));
        String access = provider.createAccessToken("10001", "hr01", List.of("RECRUITER"), 3L);
        String refresh = provider.createRefreshToken("10001", "hr01", List.of("RECRUITER"), 3L, "family-1");

        assertThrows(IllegalArgumentException.class, () -> provider.parseRefreshToken(access));
        assertThrows(IllegalArgumentException.class, () -> provider.parseAccessToken(refresh));
    }

    @Test
    void rejectsTokenWithUnknownKid() {
        String token = craftToken(currentKeys.getPrivate(), "k9", "recruit-auth", "access", 600);
        JwtTokenProvider provider = provider("k1", 120, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));

        JwtException exception = assertThrows(JwtException.class, () -> provider.parseAccessToken(token));
        assertTrue(exception.getMessage().contains("k9"));
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenProvider provider = provider("k1", -1, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));
        String token = provider.createAccessToken("10001", "hr01", List.of("RECRUITER"), 3L);

        assertThrows(ExpiredJwtException.class, () -> provider.parseAccessToken(token));
    }

    @Test
    void rejectsWrongIssuer() {
        JwtTokenProvider foreign = provider("another-auth", "k1", 120, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));
        String token = foreign.createAccessToken("10001", "hr01", List.of("RECRUITER"), 3L);

        assertThrows(IncorrectClaimException.class,
                () -> provider("k1", 120, currentKeys.getPrivate(),
                        Map.of("k1", currentKeys.getPublic())).parseAccessToken(token));
    }

    @Test
    void verifiesOldKidDuringRotation() {
        JwtTokenProvider oldProvider = provider("k1", 120, rotatedOldKeys.getPrivate(),
                Map.of("k1", rotatedOldKeys.getPublic()));
        String oldToken = oldProvider.createAccessToken("10001", "hr01", List.of("RECRUITER"), 3L);

        JwtTokenProvider rotated = provider("k2", 120, currentKeys.getPrivate(),
                Map.of("k2", currentKeys.getPublic(), "k1", rotatedOldKeys.getPublic()));

        assertEquals("10001", rotated.parseAccessToken(oldToken).userId());
        String newToken = rotated.createAccessToken("10001", "hr01", List.of("RECRUITER"), 3L);
        assertEquals("k2", Jwts.parser().verifyWith(currentKeys.getPublic()).build()
                .parseSignedClaims(newToken).getHeader().getKeyId());
    }

    @Test
    void rejectsUnknownOrMissingTokenType() {
        JwtTokenProvider provider = provider("k1", 120, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));
        String wrongType = craftToken(currentKeys.getPrivate(), "k1", "recruit-auth", "weird", 600);
        String missingType = Jwts.builder()
                .header().keyId("k1").and()
                .issuer("recruit-auth")
                .subject("10001")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("uid", "10001")
                .claim("tv", 1)
                .signWith(currentKeys.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThrows(IllegalArgumentException.class, () -> provider.parseAccessToken(wrongType));
        assertThrows(IllegalArgumentException.class, () -> provider.parseAccessToken(missingType));
    }

    @Test
    void rejectsBlankAndMalformedToken() {
        JwtTokenProvider provider = provider("k1", 120, currentKeys.getPrivate(),
                Map.of("k1", currentKeys.getPublic()));

        assertThrows(IllegalArgumentException.class, () -> provider.parseAccessToken(" "));
        assertThrows(JwtException.class, () -> provider.parseAccessToken("not-a-jwt"));
    }

    private static JwtTokenProvider provider(String keyId, long accessTtlMinutes,
                                             PrivateKey privateKey, Map<String, PublicKey> publicKeys) {
        return provider("recruit-auth", keyId, accessTtlMinutes, privateKey, publicKeys);
    }

    private static JwtTokenProvider provider(String issuer, String keyId, long accessTtlMinutes,
                                             PrivateKey privateKey, Map<String, PublicKey> publicKeys) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(issuer);
        properties.setKeyId(keyId);
        properties.setAccessTokenTtlMinutes(accessTtlMinutes);
        properties.setRefreshTokenTtlDays(7);
        properties.setClockSkewSeconds(30);
        return new JwtTokenProvider(properties, privateKey, publicKeys);
    }

    private static String craftToken(PrivateKey privateKey, String kid, String issuer,
                                     String type, long ttlSeconds) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .issuer(issuer)
                .subject("10001")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)))
                .claim("uid", "10001")
                .claim("uname", "hr01")
                .claim("roles", List.of("RECRUITER"))
                .claim("tv", 3)
                .claim("typ", type)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
