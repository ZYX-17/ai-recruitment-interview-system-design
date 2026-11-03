package com.recruit.interview.support;

import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import com.recruit.common.trace.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级 {@code @PreAuthorize} 失败出口：403 {@code AUTH1101}。
 *
 * <p>异常发生在 Security 过滤器链之后，{@code ExceptionTranslationFilter} 接不到；
 * 不单独处理会被兜底成 500。</p>
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("权限不足: traceId={}, message={}", TraceIdUtil.get(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.AUTH_FORBIDDEN.getHttpStatus())
                .body(Result.fail(ErrorCode.AUTH_FORBIDDEN));
    }
}
