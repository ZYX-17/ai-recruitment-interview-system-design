package com.recruit.common.exception;

import com.recruit.common.result.ErrorCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 可预期的业务异常。异常边界统一由 GlobalExceptionHandler 转换为 Result + HTTP 状态码。
 *
 * <p>message 为空时使用 ErrorCode 的默认文案；不要把 SQL、文件路径或第三方响应体放进 message。</p>
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(resolveMessage(errorCode, message), cause);
        this.errorCode = errorCode == null ? ErrorCode.SYS_ERROR : errorCode;
    }

    private static String resolveMessage(ErrorCode errorCode, String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return (errorCode == null ? ErrorCode.SYS_ERROR : errorCode).getDefaultMessage();
    }
}
