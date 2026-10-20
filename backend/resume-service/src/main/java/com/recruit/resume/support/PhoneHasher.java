package com.recruit.resume.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * 手机号查重摘要：HMAC-SHA256(pepper, phone)。
 *
 * <p>为什么不用裸 SHA-256：手机号空间小，裸哈希可被彩虹表反推；加一个只有服务端知道的
 * pepper 后，即使库泄露也无法离线枚举。pepper 从环境变量注入，生产环境必须替换默认值。</p>
 */
@Component
public class PhoneHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] pepper;

    public PhoneHasher(@Value("${recruit.dedup.phone-pepper:dev-phone-pepper}") String pepper) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param phone 手机号；空值返回 null（允许 HR 只填姓名）
     * @return 64 位小写十六进制摘要
     */
    public String hash(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            byte[] digest = mac.doFinal(phone.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM 不支持 " + ALGORITHM, e);
        }
    }
}
