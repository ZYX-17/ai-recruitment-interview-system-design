package com.recruit.auth.support;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PhoneCipher} 的加密、盲索引与脱敏测试。
 */
class PhoneCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptThenDecryptRoundTripsWithoutLeakingPlainText() {
        PhoneCipher cipher = new PhoneCipher(KEY);

        String encrypted = cipher.encrypt("13800000000");

        assertTrue(cipher.isConfigured());
        assertNotEquals("13800000000", encrypted);
        assertEquals("13800000000", cipher.decrypt(encrypted));
        assertNotEquals(encrypted, cipher.encrypt("13800000000"));
    }

    @Test
    void hashIsStableAndSha256Sized() {
        PhoneCipher cipher = new PhoneCipher(KEY);

        String first = cipher.hash(" 13800000000 ");
        String second = cipher.hash("13800000000");

        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void maskKeepsPrefixAndSuffix() {
        PhoneCipher cipher = new PhoneCipher(KEY);

        assertEquals("138****0000", cipher.mask("13800000000"));
        assertEquals("****", cipher.mask("123"));
        assertNull(cipher.mask(null));
    }

    @Test
    void missingKeyDisablesPhoneStorageButBeanStillStarts() {
        PhoneCipher cipher = new PhoneCipher("");

        assertFalse(cipher.isConfigured());
        assertThrows(IllegalStateException.class, () -> cipher.encrypt("13800000000"));
        assertNull(cipher.decrypt("anything"));
    }

    @Test
    void rejectsInvalidKeyLength() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[8]);

        assertThrows(IllegalStateException.class, () -> new PhoneCipher(tooShort));
    }
}
