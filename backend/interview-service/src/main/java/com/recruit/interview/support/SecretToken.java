package com.recruit.interview.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 门户免登录 token 的生成与哈希。
 *
 * <p>明文 token 32 bytes，只出现在一次性链接/响应里；库中只存 {@code sha256}。</p>
 */
public final class SecretToken {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretToken() {
    }

    /**
     * 生成 URL 安全的随机 token。
     *
     * @return 明文 token
     */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算 {@code sha256} 十六进制。
     *
     * @param value 明文
     * @return 64 位小写十六进制
     */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.SYS_ERROR, "token 哈希失败", e);
        }
    }
}
