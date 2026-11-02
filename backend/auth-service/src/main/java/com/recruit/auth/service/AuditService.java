package com.recruit.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.auth.entity.IamAuditLog;
import com.recruit.auth.mapper.IamAuditLogMapper;
import com.recruit.common.trace.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 审计写入（只增不改）。
 *
 * <p>只记录动作与脱敏摘要，禁止写入密码、令牌、完整手机号/简历正文。
 * 登录失败也写审计，{@code operatorId} 为空表示匿名。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final IamAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 写一条审计。
     *
     * @param operatorId   操作者，匿名传 null
     * @param action       动作码，如 LOGIN
     * @param resourceType 资源类型，如 USER
     * @param resourceId   资源 ID，可为 null
     * @param success      是否成功
     * @param ip           客户端 IP
     * @param detail       脱敏摘要，可为 null
     */
    public void record(Long operatorId, String action, String resourceType, String resourceId,
                       boolean success, String ip, Map<String, Object> detail) {
        IamAuditLog row = new IamAuditLog();
        row.setOperatorId(operatorId);
        row.setAction(truncate(action, 64));
        row.setResourceType(truncate(resourceType, 64));
        row.setResourceId(truncate(resourceId, 64));
        row.setResult(success ? "OK" : "FAIL");
        row.setIp(truncate(ip, 64));
        row.setTraceId(truncate(TraceIdUtil.get(), 64));
        row.setDetail(toJson(detail));
        auditLogMapper.insert(row);
    }

    /**
     * 写审计但不让审计失败影响主流程（登录失败路径使用）。
     *
     * @param operatorId   操作者
     * @param action       动作码
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param success      是否成功
     * @param ip           客户端 IP
     * @param detail       脱敏摘要
     */
    public void recordQuietly(Long operatorId, String action, String resourceType, String resourceId,
                              boolean success, String ip, Map<String, Object> detail) {
        try {
            record(operatorId, action, resourceType, resourceId, success, ip, detail);
        } catch (RuntimeException e) {
            log.error("审计写入失败: action={}, resourceType={}", action, resourceType, e);
        }
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            log.warn("审计摘要序列化失败，降级为空对象", e);
            return "{}";
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
