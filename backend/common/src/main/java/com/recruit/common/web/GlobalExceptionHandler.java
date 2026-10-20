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
 * 全局异常出口：所有 Controller 抛出的异常都在这里转成统一的 {@link Result} JSON。
 *
 * <p>为什么要统一：如果每个 Controller 各写 try/catch，容易出现
 * “有的接口返回 200+code=500，有的接口返回 500+HTML 错误页”，前端无法统一处理。
 * 这里保证三件事：</p>
 * <ol>
 *   <li>响应体结构永远是 {@code {code,message,data,traceId}}（由 {@link Result} 定义）；</li>
 *   <li>HTTP 状态码按 {@link ErrorCode#getHttpStatus()} 返回，便于网关、监控和浏览器正确识别；</li>
 *   <li>4xx 只记 WARN，5xx 记 ERROR，且返回给客户端的 message 不包含堆栈、SQL、文件路径。</li>
 * </ol>
 *
 * <p>注意：这里只处理“已经冒到 Controller 层”的异常。参数校验在进入方法前失败时，
 * 也会被 Spring 包装成下面这些异常类型，所以同样会走到这里。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 返回给客户端的错误文案最大长度。
     * 校验失败时可能拼出很长的字段列表，截断可以避免响应体过大，也避免把内部字段结构全部暴露出去。
     */
    private static final int MAX_MESSAGE_LENGTH = 300;

    /**
     * 处理业务异常。
     *
     * <p>HTTP 状态码直接取错误码上配置的值，例如：
     * APP_NOT_FOUND(404)、SCHEDULE_CONFLICT(409)、GW_UNAUTHENTICATED(401)。
     * 响应体里的 code 仍然是业务码（如 ITV3001），两者互不替代。</p>
     *
     * <p>日志级别：5xx 说明是服务端问题，记 ERROR 并带堆栈；4xx 是调用方问题，记 WARN 即可，
     * 避免正常的参数错误把错误日志刷满。</p>
     *
     * @param ex 业务异常
     * @return 带 HTTP 状态码的统一响应
     */
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
     * 处理表单/Query 对象绑定失败，以及 {@code @RequestBody} 上的 Bean Validation 失败。
     *
     * <p>为什么一个方法能覆盖两种场景：Spring 6 中
     * {@code MethodArgumentNotValidException} 继承自 {@link BindException}，
     * 所以声明父类型即可同时接住两者，不需要重复写两个 handler。</p>
     *
     * <p>返回示例：{@code pageSize: 每页最多 100 条}。</p>
     *
     * @param ex 绑定异常
     * @return 400 + SYS400
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        return badRequest(formatFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    /**
     * 处理方法参数上的约束校验失败（{@code @Validated} + {@code @Min/@NotBlank} 等）。
     *
     * <p>典型场景：{@code public Result<?> list(@RequestParam @Min(1) int pageNum)}。</p>
     *
     * @param ex 约束校验异常
     * @return 400 + SYS400
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        // 一条请求可能同时违反多个约束，用分号拼起来一次性告诉调用方，减少来回试错。
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /**
     * 处理 Spring 6.1 起的方法参数校验异常。
     *
     * <p>背景：Spring 6.1 对 Controller 方法参数做内置校验时，失败会抛
     * {@link HandlerMethodValidationException}，不再走 {@link ConstraintViolationException}。
     * 如果不单独处理，它会落到最后的 {@code Exception} 分支被当成 500，调用方会误以为是服务端故障。</p>
     *
     * @param ex 方法参数校验异常
     * @return 400 + SYS400
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllValidationResults().stream()
                .flatMap(result -> {
                    // 参数名可能因为编译时没加 -parameters 而为 null，这里做兜底显示。
                    String parameterName = result.getMethodParameter().getParameterName();
                    return result.getResolvableErrors().stream()
                            .map(error -> (parameterName == null ? "参数" : parameterName) + ": "
                                    + (error.getDefaultMessage() == null ? "参数非法" : error.getDefaultMessage()));
                })
                .collect(Collectors.joining("; "));
        return badRequest(message);
    }

    /**
     * 处理请求体无法解析：JSON 语法错误、字段类型对不上、空 body 等。
     *
     * <p>不把底层解析异常原文返回给客户端，因为它可能包含类名、字段路径等内部结构。</p>
     *
     * @param ex 请求体解析异常
     * @return 400 + SYS400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return badRequest("请求体格式错误");
    }

    /**
     * 处理必填的 Query 参数缺失，例如接口声明了 {@code @RequestParam String keyword} 但没传。
     *
     * @param ex 缺参异常
     * @return 400 + SYS400，并指出缺的是哪个参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequest("缺少请求参数: " + ex.getParameterName());
    }

    /**
     * 处理参数类型转换失败，例如把 {@code abc} 传给 {@code Long id}。
     *
     * @param ex 类型不匹配异常
     * @return 400 + SYS400，并指出是哪个参数
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("参数类型错误: " + ex.getName());
    }

    /**
     * 兜底处理所有未预期异常。
     *
     * <p>安全要求：只返回统一的 SYS500 文案，绝不把 {@code ex.getMessage()} 或堆栈返回给客户端，
     * 否则可能泄露数据库地址、SQL、密钥等敏感信息。排查时用日志里的 traceId 关联。</p>
     *
     * @param ex 未预期异常
     * @return 500 + SYS500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex) {
        log.error("未处理异常: traceId={}", TraceIdUtil.get(), ex);
        return ResponseEntity.status(ErrorCode.SYS_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYS_ERROR));
    }

    /**
     * 构造 400 响应的公共方法，保证所有参数类错误的结构和状态码完全一致。
     *
     * @param message 错误文案；为空时使用 SYS400 的默认文案
     * @return 400 + SYS400
     */
    private ResponseEntity<Result<Void>> badRequest(String message) {
        String safeMessage = truncate(message);
        Result<Void> body = safeMessage == null
                ? Result.fail(ErrorCode.PARAM_INVALID)
                : Result.fail(ErrorCode.PARAM_INVALID, safeMessage);
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus()).body(body);
    }

    /**
     * 把字段校验错误拼成一行可读文案，例如：{@code pageSize: 每页最多 100 条; pageNum: 必须大于 0}。
     *
     * @param errors 字段错误列表，可能为空
     * @return 拼接后的文案；没有错误时返回 null
     */
    private String formatFieldErrors(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .map(error -> error.getField() + ": "
                        + (error.getDefaultMessage() == null ? "参数非法" : error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
    }

    /**
     * 截断过长的错误文案，并把空串转成 null（交给调用方使用默认文案）。
     *
     * @param message 原始文案
     * @return 截断后的文案；null 或空白返回 null
     */
    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= MAX_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_MESSAGE_LENGTH);
    }
}
