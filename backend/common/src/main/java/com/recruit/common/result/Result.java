package com.recruit.common.result;

import com.recruit.common.trace.TraceIdUtil;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应包装。前端约定：code=="0" 为成功，否则按错误码表（开发文档 3.3）处理。
 */
@Data
public class Result<T> implements Serializable {

    private String code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceIdUtil.get();
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMessage(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getDefaultMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(code);
    }
}
