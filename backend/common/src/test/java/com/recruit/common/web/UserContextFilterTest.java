package com.recruit.common.web;

import com.recruit.common.context.UserContext;
import com.recruit.common.trace.TraceIdUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserContextFilter} 的单元测试。
 *
 * <p>重点验证：可信头解析（含 Base64 中文姓名）、请求结束后清理 ThreadLocal 与 MDC、
 * 缺少用户 ID 时保持匿名。</p>
 */
class UserContextFilterTest {

    private final UserContextFilter filter = new UserContextFilter();

    /**
     * 即使断言失败，也要清理线程变量，避免影响后续用例。
     */
    @AfterEach
    void cleanup() {
        UserContext.clear();
        TraceIdUtil.clear();
    }

    /**
     * 模拟网关写入四个头：uid、角色、Base64 姓名、traceId。
     * 在过滤器链内部读取上下文，链结束后确认全部被清理。
     */
    @Test
    void readsTrustedHeadersAndClearsAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_ID, "10001");
        // 角色头故意带空格，验证解析时会 trim。
        request.addHeader(UserContext.HEADER_USER_ROLE, "HR_ADMIN, INTERVIEWER");
        request.addHeader(UserContext.HEADER_USER_NAME_B64,
                Base64.getEncoder().encodeToString("张三".getBytes(StandardCharsets.UTF_8)));
        request.addHeader(UserContext.HEADER_TRACE_ID, "trace-1");

        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> displayName = new AtomicReference<>();
        AtomicReference<String> traceId = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            userId.set(UserContext.getUserId());
            displayName.set(UserContext.getDisplayName());
            traceId.set(TraceIdUtil.get());
            assertTrue(UserContext.hasRole("INTERVIEWER"));
        });

        assertEquals("10001", userId.get());
        assertEquals("张三", displayName.get());
        assertEquals("trace-1", traceId.get());
        // 请求结束后必须清理，模拟 Tomcat 线程复用场景。
        assertNull(UserContext.getUserId());
        assertTrue(TraceIdUtil.get().isEmpty());
    }

    /**
     * 只有角色头、没有用户 ID 头时，不能建立登录态（防止伪造角色就当成已登录）。
     */
    @Test
    void leavesContextEmptyWhenUserIdHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_ROLE, "HR_ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertNull(UserContext.getUserId()));

        assertNull(UserContext.getUserId());
    }
}
