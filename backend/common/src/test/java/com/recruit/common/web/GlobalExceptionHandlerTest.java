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

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBizExceptionToHttpStatusAndCode() {
        ResponseEntity<Result<Void>> response =
                handler.handleBizException(new BizException(ErrorCode.APP_NOT_FOUND));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ITV3001", response.getBody().getCode());
        assertEquals("投递不存在", response.getBody().getMessage());
    }

    @Test
    void keepsCustomBizMessage() {
        ResponseEntity<Result<Void>> response = handler.handleBizException(
                new BizException(ErrorCode.PARAM_INVALID, "pageSize 不能超过 100"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("pageSize 不能超过 100", response.getBody().getMessage());
    }

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
