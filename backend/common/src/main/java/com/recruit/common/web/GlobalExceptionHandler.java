package com.recruit.common.web;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import com.recruit.common.trace.TraceIdUtil;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一异常边界：业务码放 body，HTTP 状态码按 ErrorCode 语义返回。
 *
 * <p>只记录一次异常；4xx 用 WARN，5xx 用 ERROR。返回给客户端的 message 必须是可展示文案，
 * 不包含 SQL、堆栈、文件路径或第三方响应体。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MAX_MESSAGE_LENGTH = 300;

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        int status = errorCode.getHttpStatus();
        if (status >= 500) {
            log.error("业务异常: code={}, message={}", errorCode.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("业务异常: code={}, message={}", errorCode.getCode(), ex.getMessage());
        }
        return ResponseEntity.status(status).body(Result.fail(errorCode, ex.getMessage()));
    }

    /**
     * 同时覆盖 MethodArgumentNotValidException（@RequestBody 校验）和 BindException（表单绑定）。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        return badRequest(formatFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /**
     * Spring 6.1 ? @Validated ????????? HandlerMethodValidationException?
     * ??? ConstraintViolationException??????? 400?
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllValidationResults().stream()
                .flatMap(result -> {
                    String parameterName = result.getMethodParameter().getParameterName();
                    return result.getResolvableErrors().stream()
                            .map(error -> (parameterName == null ? "??" : parameterName) + ": "
                                    + (error.getDefaultMessage() == null ? "????" : error.getDefaultMessage()));
                })
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return badRequest("请求体格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("缺少请求参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("参数类型错误: " + ex.getName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("未处理异常: traceId={}", TraceIdUtil.get(), ex);
        return ResponseEntity.status(ErrorCode.SYS_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYS_ERROR));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        String safeMessage = truncate(message);
        Result<Void> body = safeMessage == null
                ? Result.fail(ErrorCode.PARAM_INVALID)
                : Result.fail(ErrorCode.PARAM_INVALID, safeMessage);
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus()).body(body);
    }

    private String formatFieldErrors(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .map(error -> error.getField() + ": "
                        + (error.getDefaultMessage() == null ? "参数非法" : error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
