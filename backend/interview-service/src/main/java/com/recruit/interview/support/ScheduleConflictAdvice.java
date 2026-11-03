package com.recruit.interview.support;

import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.ConflictCheckResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 排期冲突出口：409 {@code ITV3101}，并在 {@code data} 中返回全部冲突明细。
 *
 * <p>比 common 的 {@code GlobalExceptionHandler} 更具体（子类异常），Spring 会优先匹配本处理器。</p>
 */
@RestControllerAdvice
public class ScheduleConflictAdvice {

    /**
     * 处理排期冲突。
     *
     * @param ex 冲突异常
     * @return 409 + 冲突明细
     */
    @ExceptionHandler(ScheduleConflictException.class)
    public ResponseEntity<Result<ConflictCheckResult>> handle(ScheduleConflictException ex) {
        Result<ConflictCheckResult> body = Result.fail(ErrorCode.SCHEDULE_CONFLICT, ex.getMessage());
        body.setData(new ConflictCheckResult(true, ex.getConflicts()));
        return ResponseEntity.status(ErrorCode.SCHEDULE_CONFLICT.getHttpStatus()).body(body);
    }
}
