package com.recruit.auth.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取（只用于审计，不参与鉴权）。
 *
 * <p>网关会覆盖/注入 {@code X-Forwarded-For}；直连时该头可能被伪造，
 * 因此这里的结果只能写审计，不能作为风控唯一依据。</p>
 */
public final class IpUtil {

    private static final int MAX_IP_LENGTH = 64;

    private IpUtil() {
    }

    /**
     * 取客户端 IP：优先 X-Forwarded-For 第一个地址，其次 X-Real-IP，最后 remoteAddr。
     *
     * @param request HTTP 请求
     * @return 截断后的 IP；拿不到时返回 null
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim());
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_IP_LENGTH ? value : value.substring(0, MAX_IP_LENGTH);
    }
}
