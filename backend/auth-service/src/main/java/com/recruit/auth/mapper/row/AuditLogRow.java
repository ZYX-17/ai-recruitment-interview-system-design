package com.recruit.auth.mapper.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计/登录日志分页的数据库投影。
 *
 * <p>左连 {@code iam_user} 带出操作者登录名与显示名；匿名失败登录的 operator 为空。</p>
 */
@Data
public class AuditLogRow {

    private Long id;
    private Long operatorId;
    private String operatorUsername;
    private String operatorName;
    private String action;
    private String resourceType;
    private String resourceId;
    private String result;
    private String ip;
    private String traceId;

    /** 脱敏变更摘要，原样返回 JSON 字符串。 */
    private String detail;

    private LocalDateTime createdAt;
}
