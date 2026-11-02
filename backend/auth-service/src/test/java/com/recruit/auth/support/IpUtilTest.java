package com.recruit.auth.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link IpUtil} 的单元测试。
 */
class IpUtilTest {

    @Test
    void prefersFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        assertEquals("203.0.113.9", IpUtil.clientIp(request));
    }

    @Test
    void fallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.7");
        request.setRemoteAddr("10.0.0.2");
        assertEquals("198.51.100.7", IpUtil.clientIp(request));

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("10.0.0.2");
        assertEquals("10.0.0.2", IpUtil.clientIp(direct));
    }

    @Test
    void returnsNullWhenRemoteAddrBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("");
        assertNull(IpUtil.clientIp(request));
    }
}
