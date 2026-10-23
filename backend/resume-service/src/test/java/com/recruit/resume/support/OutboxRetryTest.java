package com.recruit.resume.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxRetryTest {

    @Test
    void backoffIsExponential() {
        assertEquals(1, OutboxRetry.backoffMinutes(1));
        assertEquals(2, OutboxRetry.backoffMinutes(2));
        assertEquals(4, OutboxRetry.backoffMinutes(3));
        assertEquals(8, OutboxRetry.backoffMinutes(4));
        assertEquals(16, OutboxRetry.backoffMinutes(5));
        assertEquals(16, OutboxRetry.backoffMinutes(9));
    }

    @Test
    void deadAfterFiveRetries() {
        assertFalse(OutboxRetry.isDead(4));
        assertTrue(OutboxRetry.isDead(5));
        assertTrue(OutboxRetry.isDead(6));
    }

    @Test
    void rejectsNonPositiveRetryCount() {
        assertThrows(IllegalArgumentException.class, () -> OutboxRetry.backoffMinutes(0));
    }
}
