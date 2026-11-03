package com.recruit.gateway.security;

import com.recruit.gateway.config.GatewaySecurityProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link GatewayJwtVerifier} 的验签测试：类型、issuer、签名、uid 缺一不可。
 */
class GatewayJwtVerifierTest {

    private static KeyPair keys;
    private static KeyPair otherKeys;

    private GatewaySecurityProperties.Jwt properties;
    private GatewayJwtVerifier verifier;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        properties = new GatewaySecurityProperties().getJwt();
        properties.setIssuer("recruit-auth");
        properties.setKeyId("k1");
        verifier = new GatewayJwtVerifier(properties, Map.of("k1", keys.getPublic()));
    }

    @Test
    void validAccessTokenIsParsed() {
        String token = token("access", "recruit-auth", "10001", List.of("HR", "INTERVIEWER"), 3L, keys);

        GatewayUser user = verifier.verifyAccessToken(token);

        assertEquals("10001", user.userId());
        assertEquals(List.of("HR", "INTERVIEWER"), user.roles());
        assertEquals("jti-1", user.jti());
        assertEquals(3L, user.tokenVersion());
    }

    @Test
    void refreshTokenCannotAccessBusinessApi() {
        String token = token("refresh", "recruit-auth", "10001", List.of("HR"), 3L, keys);

        assertThrows(IllegalArgumentException.class, () -> verifier.verifyAccessToken(token));
    }

    @Test
    void wrongIssuerIsRejected() {
        String token = token("access", "other-issuer", "10001", List.of("HR"), 3L, keys);

        assertThrows(JwtException.class, () -> verifier.verifyAccessToken(token));
    }

    @Test
    void wrongSignatureIsRejected() {
        String token = token("access", "recruit-auth", "10001", List.of("HR"), 3L, otherKeys);

        assertThrows(JwtException.class, () -> verifier.verifyAccessToken(token));
    }

    @Test
    void missingUidIsRejected() {
        String token = token("access", "recruit-auth", null, List.of("HR"), 3L, keys);

        assertThrows(IllegalArgumentException.class, () -> verifier.verifyAccessToken(token));
    }

    private static String token(String type, String issuer, String userId, List<String> roles,
                                long tokenVersion, KeyPair signingKeys) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .header().keyId("k1").and()
                .issuer(issuer)
                .subject(userId == null ? "subject" : userId)
                .id("jti-1")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .claim("roles", roles)
                .claim("tv", tokenVersion)
                .claim("typ", type);
        if (userId != null) {
            builder.claim("uid", userId);
        }
        return builder.signWith(signingKeys.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
