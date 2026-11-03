package com.recruit.auth.dto;

import java.time.LocalDateTime;

/**
 * 操作日志视图。
 *
 * @param id               日志 ID
 * @param operatorId       操作者用户 ID；匿名操作（登录失败）为 null
 * @param operatorUsername 操作者登录名
 * @param operatorName     操作者显示名
 * @param action           动作码
 * @param resourceType     资源类型
 * @param resourceId       资源 ID
 * @param result           OK / FAIL
 * @param ip               客户端 IP
 * @param traceId          链路 traceId
 * @param detail           脱敏变更摘要（JSON 字符串）
 * @param createdAt        发生时间
 */
public record AuditLogView(String id,
                           String operatorId,
                           String operatorUsername,
                           String operatorName,
                           String action,
                           String resourceType,
                           String resourceId,
                           String result,
                           String ip,
                           String traceId,
                           String detail,
                           LocalDateTime createdAt) {
}
