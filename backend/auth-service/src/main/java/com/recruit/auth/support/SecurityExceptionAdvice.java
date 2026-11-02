package com.recruit.auth.support;

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
 * 方法级鉴权失败的 JSON 出口。
 *
 * <p>{@code @PreAuthorize} 在 Controller 代理里抛 {@link AccessDeniedException}，
 * 这个异常发生在 Security 过滤器链之后，{@code ExceptionTranslationFilter} 接不到；
 * 若不单独处理，会被 common 的 {@code Exception} 兜底转成 500。这里保证越权
 * 与过滤器层一致返回 403 + {@code AUTH1101}（docs/4 §S2）。</p>
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    /**
     * 已认证但缺少角色/权限。
     *
     * @param ex 鉴权异常
     * @return 403 + AUTH1101
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("权限不足: traceId={}, message={}", TraceIdUtil.get(), ex.getMessage());
        return ResponseEntity.status(ErrorCode.AUTH_FORBIDDEN.getHttpStatus())
                .body(Result.fail(ErrorCode.AUTH_FORBIDDEN));
    }
}
