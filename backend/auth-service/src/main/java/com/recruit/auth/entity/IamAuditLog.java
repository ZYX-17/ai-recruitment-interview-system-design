package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计（只增不改，不记密码/令牌）。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_audit_log}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_audit_log")
public class IamAuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 匿名操作（如登录失败）为 null。 */
    private Long operatorId;

    /** LOGIN / LOGOUT / GRANT / UPDATE_MENU ... */
    private String action;

    private String resourceType;
    private String resourceId;

    /** OK / FAIL。 */
    private String result;

    private String ip;
    private String traceId;

    /** 脱敏后的变更摘要 JSON。 */
    private String detail;

    private LocalDateTime createdAt;
}
