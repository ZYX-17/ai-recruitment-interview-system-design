package com.recruit.common.context;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.trace.TraceIdUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前请求的用户上下文。
 *
 * <p>数据只信任网关注入的 X-User-* 头，禁止从请求体、Query 或前端参数中读取 uid。
 * 必须在请求结束时调用 {@link #clear()}，避免线程池复用导致用户串号。</p>
 */
public final class UserContext {

    public static final String HEADER_USER_ID = "X-User-Uid";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_NAME_B64 = "X-User-Name-B64";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(String userId, Collection<String> roles, String displayName, String traceId) {
        if (userId == null || userId.isBlank()) {
            clear();
            return;
        }
        HOLDER.set(new Snapshot(userId, roles, displayName, traceId));
    }

    public static void set(String userId, Collection<String> roles) {
        set(userId, roles, null, null);
    }

    public static String getUserId() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? null : snapshot.userId;
    }

    /**
     * 取当前用户 ID；未认证时抛出 401 业务异常，避免 Controller 各写一套判空。
     */
    public static String requireUserId() {
        String userId = getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED, "未获取到当前用户");
        }
        return userId;
    }

    public static Set<String> getRoles() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? Collections.emptySet() : snapshot.roles;
    }

    public static boolean hasRole(String role) {
        return role != null && getRoles().contains(role);
    }

    public static boolean hasAnyRole(String... roles) {
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    public static String getDisplayName() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? null : snapshot.displayName;
    }

    public static String getTraceId() {
        Snapshot snapshot = HOLDER.get();
        if (snapshot != null && snapshot.traceId != null) {
            return snapshot.traceId;
        }
        return TraceIdUtil.get();
    }

    public static boolean isAuthenticated() {
        return getUserId() != null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static final class Snapshot {

        private final String userId;
        private final Set<String> roles;
        private final String displayName;
        private final String traceId;

        private Snapshot(String userId, Collection<String> roles, String displayName, String traceId) {
            this.userId = userId;
            this.roles = roles == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
            this.displayName = displayName;
            this.traceId = traceId;
        }
    }
}
