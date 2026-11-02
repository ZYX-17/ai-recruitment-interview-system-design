package com.recruit.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 令牌哈希工具。
 *
 * <p>Redis 与 {@code iam_refresh_session} 只存 sha256，不存 Refresh Token 原文：
 * 即使 Redis/DB 泄露，攻击者也不能直接拿来换 Access Token。</p>
 */
public final class TokenHash {

    private TokenHash() {
    }

    /**
     * 计算 SHA-256 小写十六进制。
     *
     * @param value 原值
     * @return 64 位十六进制字符串
     */
    public static String sha256(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("待哈希的令牌不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}
