package com.recruit.common.exception;

import com.recruit.common.result.ErrorCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常：表示“代码能预料到、并且应该按业务语义返回给调用方”的失败。
 *
 * <p>设计意图：</p>
 * <ul>
 *   <li>Service 层只负责抛出 {@code new BizException(ErrorCode.XXX)}，不需要关心 HTTP 状态码；</li>
 *   <li>HTTP 状态码由 {@link ErrorCode#getHttpStatus()} 决定，统一在 GlobalExceptionHandler 落地；</li>
 *   <li>沿用 {@link RuntimeException}（非受检异常），避免业务方法签名被 {@code throws} 污染。</li>
 * </ul>
 *
 * <p>message 约定：为空时自动使用 ErrorCode 的默认文案。
 * message 是直接展示给用户的，不要放 SQL、文件路径、堆栈或第三方响应体。</p>
 *
 * <p>示例：{@code throw new BizException(ErrorCode.APP_NOT_FOUND);}</p>
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码。GlobalExceptionHandler 用它同时决定：
     * 响应体里的 code/message，以及 HTTP 状态码。
     */
    private final ErrorCode errorCode;

    /**
     * 只传错误码，使用错误码自带的默认文案。
     *
     * @param errorCode 业务错误码，不能为 null（为 null 时按 SYS500 处理）
     */
    public BizException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    /**
     * 传错误码 + 自定义文案。需要补充上下文时使用（例如“投递 123 不存在”）。
     *
     * @param errorCode 业务错误码
     * @param message   展示给用户的文案；为空时回退到错误码默认文案
     */
    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * 传错误码 + 根因。用于保留底层异常（如 Redis、HTTP 客户端异常）以便排查，
     * 但根因信息不会返回给客户端。
     *
     * @param errorCode 业务错误码
     * @param cause     原始异常
     */
    public BizException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, null, cause);
    }

    /**
     * 完整构造。
     *
     * @param errorCode 业务错误码
     * @param message   展示给用户的文案；为空时回退到错误码默认文案
     * @param cause     原始异常，可为 null
     */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        // 先算好最终文案再交给父类，保证 ex.getMessage() 永远不为空，
        // 日志和响应体都不需要再判空。
        super(resolveMessage(errorCode, message), cause);
        // errorCode 兜底成 SYS500：即使调用方手滑传了 null，也不会在异常处理器里 NPE。
        this.errorCode = errorCode == null ? ErrorCode.SYS_ERROR : errorCode;
    }

    /**
     * 计算最终展示文案：优先使用调用方传入的 message，否则用错误码默认文案。
     *
     * @param errorCode 业务错误码，可为 null
     * @param message   自定义文案，可为空
     * @return 非空的展示文案
     */
    private static String resolveMessage(ErrorCode errorCode, String message) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return (errorCode == null ? ErrorCode.SYS_ERROR : errorCode).getDefaultMessage();
    }
}
