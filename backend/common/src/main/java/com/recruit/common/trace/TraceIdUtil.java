package com.recruit.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * traceId 工具：网关生成 → MDC 透传 → 日志/事件信封携带。
 * 日志格式建议：%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n
 */
public final class TraceIdUtil {

    public static final String TRACE_ID_KEY = "traceId";

    private TraceIdUtil() {
    }

    /** 生成并写入 MDC（网关入口调用）。 */
    public static String generate() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID_KEY, traceId);
        return traceId;
    }

    /** 已有 traceId（如从请求头/事件信封透传）时写入 MDC。 */
    public static void put(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static String get() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null ? "" : traceId;
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
