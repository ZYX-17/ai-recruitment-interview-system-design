package com.recruit.common.web;

import com.recruit.common.context.UserContext;
import com.recruit.common.trace.TraceIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户上下文过滤器：把网关校验 JWT 后注入的可信头，还原成当前线程的 {@link UserContext}。
 *
 * <p>请求处理链路：</p>
 * <pre>
 * 浏览器 --(JWT)--> 网关 --(校验 JWT，覆写 X-User-*)--> 业务服务
 *                                                     |
 *                                          本过滤器读取头并写入 ThreadLocal
 *                                                     |
 *                                              Controller / Service 读取
 *                                                     |
 *                                          finally 中清理，防止线程复用串号
 * </pre>
 *
 * <p><b>安全前提：</b>业务服务只允许被网关访问，且网关必须“先删除客户端自带的 X-User-* 再写入
 * 校验后的值”。否则客户端可以自己伪造 {@code X-User-Uid} 冒充任意用户。</p>
 *
 * <p><b>异步注意：</b>{@link OncePerRequestFilter} 默认不参与 async dispatch。如果接口使用
 * {@code @Async} 或 Servlet 异步，ThreadLocal 在初始请求返回时就会被清理，
 * 异步线程里读不到用户上下文，需要显式传参或在异步任务开头重新设置。</p>
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UserContextFilter extends OncePerRequestFilter {

    /**
     * 每个请求只执行一次（{@link OncePerRequestFilter} 保证），顺序尽量靠前。
     *
     * <p>为什么是 {@code HIGHEST_PRECEDENCE + 20}：需要早于业务过滤器执行，
     * 但又给更早的链路组件（如 +10 的 traceId 过滤器、日志过滤器）留出位置。</p>
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 先恢复 traceId，保证本过滤器之后的所有日志都能带上链路 ID。
        String traceId = trimToNull(request.getHeader(UserContext.HEADER_TRACE_ID));
        if (traceId != null) {
            TraceIdUtil.put(traceId);
        }

        // 2. 有用户 ID 才建立上下文。匿名请求（如登录、健康检查）保持未认证状态。
        String userId = trimToNull(request.getHeader(UserContext.HEADER_USER_ID));
        if (userId != null) {
            UserContext.set(
                    userId,
                    parseRoles(request.getHeader(UserContext.HEADER_USER_ROLE)),
                    decodeDisplayName(request.getHeader(UserContext.HEADER_USER_NAME_B64)),
                    traceId
            );
        }

        try {
            // 3. 放行，执行业务 Controller。
            filterChain.doFilter(request, response);
        } finally {
            // 4. 无论成功、失败还是抛异常都必须清理。
            //    Tomcat 线程是复用的，不清理会让下一个请求读到上一个用户，造成越权。
            UserContext.clear();
            if (traceId != null) {
                TraceIdUtil.clear();
            }
        }
    }

    /**
     * 解析逗号分隔的角色头，例如 {@code "HR_ADMIN,INTERVIEWER"}。
     *
     * <p>处理细节：去掉每个角色前后的空格，忽略空字符串，用 {@link LinkedHashSet}
     * 去重并保留网关传入的顺序（日志排查时更直观）。</p>
     *
     * @param rawRoles 原始头值，可为 null
     * @return 不可变之外的可变集合由 {@link UserContext} 再包一层只读；这里返回空集合而不是 null
     */
    private Set<String> parseRoles(String rawRoles) {
        String value = trimToNull(rawRoles);
        if (value == null) {
            return Collections.emptySet();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : value.split(",")) {
            String trimmed = role.trim();
            if (!trimmed.isEmpty()) {
                roles.add(trimmed);
            }
        }
        return roles;
    }

    /**
     * 解码 Base64 编码的显示姓名（UTF-8）。
     *
     * <p>为什么先试标准 Base64、再试 URL-safe Base64：标准字母表包含 {@code + /}，
     * 某些网关或代理会把它转义；URL-safe 字母表用 {@code - _}，更适合放在 Header 里。
     * 两种都兼容，网关用哪种都不会导致姓名丢失。</p>
     *
     * <p>解析失败只记 DEBUG 并返回 null，不让一个展示字段影响主流程。</p>
     *
     * @param rawName Base64 字符串，可为 null
     * @return 解码后的姓名；为空或解码失败返回 null
     */
    private String decodeDisplayName(String rawName) {
        String value = trimToNull(rawName);
        if (value == null) {
            return null;
        }
        try {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException standardFailure) {
                // 标准字母表解不了，再用 URL-safe 字母表试一次。
                bytes = Base64.getUrlDecoder().decode(value);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            log.debug("X-User-Name-B64 解析失败，忽略该头");
            return null;
        }
    }

    /**
     * 去掉首尾空白；空串统一转成 null，避免 {@code "  "} 被当成有效用户 ID。
     *
     * @param value 原始值
     * @return 去空白后的值；null 或空白返回 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
