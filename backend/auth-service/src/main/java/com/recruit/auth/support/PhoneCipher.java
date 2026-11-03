package com.recruit.auth.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 手机号加密与盲索引（docs/4 §5.2.4）。
 *
 * <p>存储口径：</p>
 * <ul>
 *   <li>{@code phone_enc}：AES-256-GCM 密文，格式 {@code Base64(iv || ciphertext)}；</li>
 *   <li>{@code phone_hash}：手机号 SHA-256 十六进制，用于唯一性与查重，不可逆。</li>
 * </ul>
 *
 * <p>密钥来自 {@code PHONE_ENC_KEY}（Base64 的 16/24/32 字节）。未配置时不接受手机号，
 * 但服务仍可启动——手机号是可选字段，避免本地开发被密钥卡住。</p>
 */
@Component
public class PhoneCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param base64Key Base64 编码的 AES 密钥；为空表示未配置
     */
    public PhoneCipher(@Value("${recruit.security.phone-enc-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    /**
     * 是否已配置加密密钥。
     *
     * @return 配置了返回 true
     */
    public boolean isConfigured() {
        return key != null;
    }

    /**
     * 加密手机号。
     *
     * @param phone 明文手机号
     * @return Base64(iv || ciphertext)
     * @throws IllegalStateException 未配置密钥时抛出
     */
    public String encrypt(String phone) {
        if (key == null) {
            throw new IllegalStateException("未配置 PHONE_ENC_KEY，无法保存手机号");
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(phone.trim().getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("手机号加密失败", e);
        }
    }

    /**
     * 解密手机号；密文损坏或未配置密钥时返回 null（展示层按“无手机号”处理）。
     *
     * @param encrypted Base64(iv || ciphertext)
     * @return 明文手机号或 null
     */
    public String decrypt(String encrypted) {
        if (key == null || encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(encrypted);
            if (payload.length <= GCM_IV_BYTES) {
                return null;
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(payload, GCM_IV_BYTES, payload.length - GCM_IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            return null;
        }
    }

    /**
     * 手机号盲索引：SHA-256 十六进制。
     *
     * @param phone 明文手机号
     * @return 64 位小写十六进制
     */
    public String hash(String phone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(phone.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    /**
     * 脱敏手机号：保留前 3 位与后 4 位。
     *
     * @param phone 明文手机号
     * @return 如 138****0000；长度不足时整体打码
     */
    public String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String text = phone.trim();
        if (text.length() < 7) {
            return "****";
        }
        return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PHONE_ENC_KEY 必须是 Base64 编码", e);
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException("PHONE_ENC_KEY 解码后必须是 16/24/32 字节");
        }
        return new SecretKeySpec(raw, "AES");
    }
}
