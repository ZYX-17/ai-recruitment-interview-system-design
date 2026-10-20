package com.recruit.common.web;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link GlobalExceptionHandler} 的单元测试。
 *
 * <p>重点验证：业务码与 HTTP 状态码的映射、自定义文案保留、未知异常不泄露内部信息。</p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * APP_NOT_FOUND 应该返回 404，响应体 code 仍然是业务码 ITV3001。
     */
    @Test
    void mapsBizExceptionToHttpStatusAndCode() {
        ResponseEntity<Result<Void>> response =
                handler.handleBizException(new BizException(ErrorCode.APP_NOT_FOUND));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ITV3001", response.getBody().getCode());
        assertEquals("投递不存在", response.getBody().getMessage());
    }

    /**
     * 调用方传入的自定义文案应该原样返回，而不是被错误码默认文案覆盖。
     */
    @Test
    void keepsCustomBizMessage() {
        ResponseEntity<Result<Void>> response = handler.handleBizException(
                new BizException(ErrorCode.PARAM_INVALID, "pageSize 不能超过 100"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("pageSize 不能超过 100", response.getBody().getMessage());
    }

    /**
     * 未知异常只返回 SYS500 的统一文案，不能把异常原文（例如数据库密码）带给客户端。
     */
    @Test
    void hidesUnexpectedExceptionDetails() {
        ResponseEntity<Result<Void>> response =
                handler.handleUnexpected(new IllegalStateException("database password leaked"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SYS500", response.getBody().getCode());
        assertFalse(response.getBody().getMessage().contains("database password"));
    }
}
