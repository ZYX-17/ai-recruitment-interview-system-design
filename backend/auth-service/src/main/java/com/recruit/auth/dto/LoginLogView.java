package com.recruit.auth.dto;

import java.time.LocalDateTime;

/**
 * 登录日志视图。
 *
 * @param id         日志 ID
 * @param operatorId 用户 ID；匿名失败为 null
 * @param username   登录名
 * @param displayName 显示名
 * @param result     OK / FAIL
 * @param ip         客户端 IP
 * @param traceId    链路 traceId
 * @param detail     失败原因等脱敏摘要（JSON 字符串）
 * @param createdAt  发生时间
 */
public record LoginLogView(String id,
                           String operatorId,
                           String username,
                           String displayName,
                           String result,
                           String ip,
                           String traceId,
                           String detail,
                           LocalDateTime createdAt) {
}
