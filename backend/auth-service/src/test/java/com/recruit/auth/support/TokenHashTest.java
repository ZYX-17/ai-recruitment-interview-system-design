package com.recruit.auth.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TokenHash} 的单元测试。
 */
class TokenHashTest {

    @Test
    void hashesToLowercaseHexSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                TokenHash.sha256("abc"));
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> TokenHash.sha256(" "));
        assertThrows(IllegalArgumentException.class, () -> TokenHash.sha256(""));
    }
}
