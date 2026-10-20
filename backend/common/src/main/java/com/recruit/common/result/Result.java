package com.recruit.common.result;

import com.recruit.common.trace.TraceIdUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应包装。
 *
 * <p>所有接口（成功或失败）都返回这个结构，前端只需要写一套解析逻辑：</p>
 *
 * <pre>
 * {
 *   "code":      "0",              // "0" 表示成功，其他值见 ErrorCode / 开发文档 3.3
 *   "message":   "成功",           // 可直接展示给用户的文案
 *   "data":      { ... },          // 业务数据；失败时为 null
 *   "traceId":   "a1b2c3...",      // 链路 ID，报错时提供给后端排查
 *   "timestamp": 1720000000000     // 服务端毫秒时间戳，便于排查时钟/缓存问题
 * }
 * </pre>
 *
 * <p>判断成功请用 {@link #isSuccess()} 或比较 {@code code == "0"}，
 * 不要用 HTTP 200 判断，因为业务失败也可能返回 4xx/5xx。</p>
 *
 * <p>无参构造供 Feign/Jackson 反序列化使用：服务间调用（如 resume-service → file-service）
 * 需要把对方返回的 Result 包装还原成对象，没有无参构造 Jackson 会直接失败。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
public class Result<T> implements Serializable {

    /** 业务码；"0" 表示成功，其余为错误码（如 ITV3001、SYS400）。 */
    private String code;

    /** 展示文案；成功时为“成功”，失败时为具体原因。 */
    private String message;

    /** 业务数据；失败时为 null。 */
    private T data;

    /** 链路 ID，来自 MDC；日志和响应使用同一个值，方便关联排查。 */
    private String traceId;

    /** 服务端生成响应时的毫秒时间戳。 */
    private long timestamp;

    /**
     * 私有构造：统一在创建时补充 traceId 和 timestamp，避免调用方漏填。
     *
     * @param code    业务码
     * @param message 展示文案
     * @param data    业务数据
     */
    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceIdUtil.get();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应并携带数据。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=0 的响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMessage(), data);
    }

    /**
     * 成功响应，无数据（例如删除、状态变更接口）。
     *
     * @param <T> 数据类型
     * @return code=0、data=null 的响应
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 失败响应，使用错误码默认文案。
     *
     * @param errorCode 错误码
     * @param <T>       数据类型
     * @return 对应错误码的响应
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getDefaultMessage(), null);
    }

    /**
     * 失败响应，使用自定义文案。
     *
     * @param errorCode 错误码
     * @param message   展示给用户的文案
     * @param <T>       数据类型
     * @return 对应错误码的响应
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /**
     * 是否成功。
     *
     * @return code 等于 "0" 时返回 true
     */
    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(code);
    }
}
