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

class UserContextFilterTest {

    private final UserContextFilter filter = new UserContextFilter();

    @AfterEach
    void cleanup() {
        UserContext.clear();
        TraceIdUtil.clear();
    }

    @Test
    void readsTrustedHeadersAndClearsAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_ID, "10001");
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
        assertNull(UserContext.getUserId());
        assertTrue(TraceIdUtil.get().isEmpty());
    }

    @Test
    void leavesContextEmptyWhenUserIdHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(UserContext.HEADER_USER_ROLE, "HR_ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertNull(UserContext.getUserId()));

        assertNull(UserContext.getUserId());
    }
}
