package com.recruit.common.result;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ErrorCode} 登记约束的单测：编码唯一、HTTP 状态码落在错误区间、反查可用。
 */
class ErrorCodeTest {

    @Test
    void codesAreUnique() {
        Set<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        assertEquals(ErrorCode.values().length, codes.size(), "错误码必须全局唯一");
        codes.stream()
                .filter(code -> !code.equals(ErrorCode.SUCCESS.getCode()))
                .forEach(code -> assertTrue(code.matches("[A-Z]+\\d{3,4}"), "非法错误码格式: " + code));
    }

    @Test
    void everyCodeCarriesMessageAndErrorStatus() {
        assertEquals("0", ErrorCode.SUCCESS.getCode());
        assertEquals(200, ErrorCode.SUCCESS.getHttpStatus());

        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode == ErrorCode.SUCCESS) {
                continue;
            }
            assertFalse(errorCode.getDefaultMessage().isBlank(), errorCode.getCode() + " 缺少默认文案");
            assertTrue(errorCode.getHttpStatus() >= 400 && errorCode.getHttpStatus() <= 599,
                    errorCode.getCode() + " 的 HTTP 状态码非法");
        }
    }

    @Test
    void registersOfferAndNotifyCodes() {
        assertEquals(ErrorCode.OFFER_STATE_NOT_ALLOWED, ErrorCode.fromCode("ITV3301"));
        assertEquals(409, ErrorCode.OFFER_STATE_NOT_ALLOWED.getHttpStatus());

        assertEquals(ErrorCode.NOTIFY_TEMPLATE_INVALID, ErrorCode.fromCode("NTF6001"));
        assertEquals(400, ErrorCode.NOTIFY_TEMPLATE_INVALID.getHttpStatus());

        assertEquals(ErrorCode.NOTIFY_SEND_FAILED, ErrorCode.fromCode("NTF6002"));
        assertEquals(502, ErrorCode.NOTIFY_SEND_FAILED.getHttpStatus());
    }

    @Test
    void fromCodeRejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> ErrorCode.fromCode("NTF9999"));
    }
}
