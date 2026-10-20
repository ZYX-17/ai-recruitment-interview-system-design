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

/**
 * {@link UserContext} 的单元测试。
 *
 * <p>重点验证：用户信息读写、未认证时的 401 语义、clear 是否彻底清理。</p>
 */
class UserContextTest {

    /**
     * 每个用例结束后强制清理，避免 ThreadLocal 在测试线程之间互相污染。
     */
    @AfterEach
    void cleanup() {
        UserContext.clear();
    }

    /**
     * 写入用户后，ID、姓名、traceId、角色判断都应该正确。
     */
    @Test
    void holdsCurrentUserAndRoles() {
        UserContext.set("10001", List.of("HR_ADMIN", "INTERVIEWER"), "张三", "trace-1");

        assertEquals("10001", UserContext.getUserId());
        assertEquals("张三", UserContext.getDisplayName());
        assertEquals("trace-1", UserContext.getTraceId());
        assertTrue(UserContext.hasRole("INTERVIEWER"));
        // hasAnyRole 是 OR 语义，命中任意一个就返回 true。
        assertTrue(UserContext.hasAnyRole("CANDIDATE", "HR_ADMIN"));
        assertFalse(UserContext.hasRole("CANDIDATE"));
        assertTrue(UserContext.isAuthenticated());
    }

    /**
     * 没有用户上下文时，requireUserId 必须抛 GW_UNAUTHENTICATED，
     * 由全局异常处理器转成 HTTP 401。
     */
    @Test
    void requireUserIdThrowsWhenHeaderMissing() {
        BizException ex = assertThrows(BizException.class, UserContext::requireUserId);

        assertEquals(ErrorCode.GW_UNAUTHENTICATED, ex.getErrorCode());
        assertNull(UserContext.getUserId());
    }

    /**
     * clear 之后所有读取入口都应回到“未认证”状态。
     */
    @Test
    void clearRemovesAllContext() {
        UserContext.set("10001", List.of("RECRUITER"));
        UserContext.clear();

        assertNull(UserContext.getUserId());
        assertTrue(UserContext.getRoles().isEmpty());
        assertFalse(UserContext.isAuthenticated());
    }
}
