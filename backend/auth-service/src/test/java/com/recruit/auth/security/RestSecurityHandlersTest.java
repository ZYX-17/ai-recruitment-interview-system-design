package com.recruit.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.support.ApiErrorWriter;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全层 401/403 出口的单元测试：状态码、业务码与 JSON 结构必须与业务接口一致。
 */
class RestSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ApiErrorWriter apiErrorWriter;

    @BeforeEach
    void setUp() {
        apiErrorWriter = new ApiErrorWriter(objectMapper);
    }

    @Test
    void expiredTokenReturnsGw9001WithLoginHint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE,
                new ExpiredJwtException(null, null, "expired"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(apiErrorWriter).commence(request, response, null);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals("GW9001", body.path("code").asText());
        assertTrue(body.path("message").asText().contains("过期"));
    }

    @Test
    void anonymousRequestReturnsDefaultGw9001Message() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(apiErrorWriter)
                .commence(new MockHttpServletRequest("GET", "/api/auth/me"), response, null);

        assertEquals(401, response.getStatus());
        assertEquals("GW9001", objectMapper.readTree(response.getContentAsByteArray()).path("code").asText());
    }

    @Test
    void internalPathReturnsGw9005() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(apiErrorWriter)
                .handle(new MockHttpServletRequest("GET", "/internal/notify/send"), response, null);

        assertEquals(403, response.getStatus());
        assertEquals("GW9005", objectMapper.readTree(response.getContentAsByteArray()).path("code").asText());
    }

    @Test
    void businessPathReturnsAuth1101() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(apiErrorWriter)
                .handle(new MockHttpServletRequest("GET", "/api/admin/users"), response, null);

        assertEquals(403, response.getStatus());
        assertEquals("AUTH1101", objectMapper.readTree(response.getContentAsByteArray()).path("code").asText());
    }
}
