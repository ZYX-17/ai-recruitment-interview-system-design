package com.recruit.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志分页查询入参（A-15）。
 *
 * <p>时间区间按 UTC 落库时间过滤；{@code action}/{@code resourceType} 精确匹配，
 * {@code traceId} 用于串联一次请求的多条日志。</p>
 */
@Data
public class AuditLogPageQuery {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "pageNum 必须从 1 开始")
    private long pageNum = 1;

    /** 每页条数，最大 100。 */
    @Min(value = 1, message = "pageSize 必须大于 0")
    @Max(value = 100, message = "pageSize 不能超过 100")
    private long pageSize = 20;

    /** 操作者用户 ID。 */
    private Long operatorId;

    /** 动作码，如 LOGIN / USER_UPDATE。 */
    @Size(max = 64, message = "action 长度不能超过 64")
    private String action;

    /** 资源类型，如 USER / ROLE。 */
    @Size(max = 64, message = "resourceType 长度不能超过 64")
    private String resourceType;

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
