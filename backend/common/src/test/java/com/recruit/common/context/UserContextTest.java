package com.recruit.common.context;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextTest {

    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    @Test
    void holdsCurrentUserAndRoles() {
        UserContext.set("10001", List.of("HR_ADMIN", "INTERVIEWER"), "张三", "trace-1");

        assertEquals("10001", UserContext.getUserId());
        assertEquals("张三", UserContext.getDisplayName());
        assertEquals("trace-1", UserContext.getTraceId());
        assertTrue(UserContext.hasRole("INTERVIEWER"));
        assertTrue(UserContext.hasAnyRole("CANDIDATE", "HR_ADMIN"));
        assertFalse(UserContext.hasRole("CANDIDATE"));
        assertTrue(UserContext.isAuthenticated());
    }

    @Test
    void requireUserIdThrowsWhenHeaderMissing() {
        BizException ex = assertThrows(BizException.class, UserContext::requireUserId);

        assertEquals(ErrorCode.GW_UNAUTHENTICATED, ex.getErrorCode());
        assertNull(UserContext.getUserId());
    }

    @Test
    void clearRemovesAllContext() {
        UserContext.set("10001", List.of("RECRUITER"));
        UserContext.clear();

        assertNull(UserContext.getUserId());
        assertTrue(UserContext.getRoles().isEmpty());
        assertFalse(UserContext.isAuthenticated());
    }
}
