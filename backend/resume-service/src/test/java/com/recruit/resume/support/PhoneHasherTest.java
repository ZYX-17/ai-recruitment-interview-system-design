package com.recruit.resume.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PhoneHasherTest {

    private final PhoneHasher hasher = new PhoneHasher("test-pepper");

    @Test
    void hashIsDeterministicAnd64Hex() {
        String first = hasher.hash("13800000000");
        String second = hasher.hash("13800000000");
        assertEquals(first, second);
        assertEquals(64, first.length());
        assertEquals(first, first.toLowerCase());
    }

    @Test
    void differentPhonesProduceDifferentHashes() {
        assertNotEquals(hasher.hash("13800000000"), hasher.hash("13900000000"));
    }

    @Test
    void blankPhoneReturnsNull() {
        assertNull(hasher.hash(null));
        assertNull(hasher.hash("  "));
    }
}
