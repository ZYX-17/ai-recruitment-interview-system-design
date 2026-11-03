package com.recruit.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 登录日志分页查询入参（A-15）。
 *
 * <p>登录日志来自 {@code iam_audit_log.action = 'LOGIN'}；{@code username} 同时匹配
 * 登录名与显示名，匿名失败（用户不存在）没有用户名，只能按 IP/时间检索。</p>
 */
@Data
public class LoginLogPageQuery {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "pageNum 必须从 1 开始")
    private long pageNum = 1;

    /** 每页条数，最大 100。 */
    @Min(value = 1, message = "pageSize 必须大于 0")
    @Max(value = 100, message = "pageSize 不能超过 100")
    private long pageSize = 20;

    /** 登录名/显示名关键字。 */
    @Size(max = 64, message = "username 长度不能超过 64")
    private String username;

    /** 结果：OK / FAIL。 */
    @Pattern(regexp = "OK|FAIL", message = "result 只能是 OK/FAIL")
    private String result;

    /** 客户端 IP。 */
    @Size(max = 64, message = "ip 长度不能超过 64")
    private String ip;

    /** 链路 traceId。 */
    @Size(max = 64, message = "traceId 长度不能超过 64")
    private String traceId;

    /** 起始时间（含），ISO-8601。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /** 结束时间（含），ISO-8601。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}
