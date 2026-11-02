package com.recruit.auth.service;

import com.recruit.auth.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginAttemptService} 的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.getLogin().setMaxFailures(5);
        properties.getLogin().setLockMinutes(15);
        service = new LoginAttemptService(redisTemplate, properties);
    }

    @Test
    void lockedWhenCounterReachesThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:login:fail:hr01")).thenReturn("5");

        assertTrue(service.isLocked("hr01"));
    }

    @Test
    void notLockedBelowThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:login:fail:hr01")).thenReturn("4");

        assertFalse(service.isLocked("hr01"));
    }

    @Test
    void missingKeyIsNotLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:login:fail:hr01")).thenReturn(null);

        assertFalse(service.isLocked("hr01"));
    }

    @Test
    void usernameIsLowercasedToAvoidCaseBypass() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:login:fail:hr01")).thenReturn("5");

        assertTrue(service.isLocked("HR01"));
    }

    @Test
    void recordFailureIncrementsAndRefreshesWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:login:fail:hr01")).thenReturn(3L);

        long failures = service.recordFailure("hr01");

        assertTrue(failures == 3L);
        verify(redisTemplate).expire("auth:login:fail:hr01", Duration.ofMinutes(15));
    }

    @Test
    void resetDeletesCounter() {
        service.reset("hr01");

        verify(redisTemplate).delete("auth:login:fail:hr01");
    }
}
