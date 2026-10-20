package com.recruit.common.context;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.trace.TraceIdUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 当前请求的用户上下文（ThreadLocal 版）。
 *
 * <p>解决什么问题：Controller 拿到网关注入的用户信息后，如果不往下传参，
 * Service/Mapper 层就取不到“当前是谁在操作”。每个方法都加一个 userId 参数又会污染所有签名，
 * 所以这里用 ThreadLocal 把“当前请求的用户”挂在执行线程上，业务代码随时可读。</p>
 *
 * <p><b>信任边界（非常重要）：</b>这里的数据只信任网关校验 JWT 后注入的 {@code X-User-*} 头。
 * 严禁从请求体、Query、前端自定义参数里读取 uid，否则调用方可以伪造身份越权。
 * 服务端口不应直接暴露公网，必须经由网关访问。</p>
 *
 * <p><b>线程安全：</b>ThreadLocal 的值只对当前线程可见。Web 容器（Tomcat）会复用线程，
 * 如果请求结束不清理，下一个请求可能读到上一个用户的身份，造成“串号”越权。
 * 因此 {@code UserContextFilter} 必须在 finally 中调用 {@link #clear()}。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 * String uid = UserContext.requireUserId();      // 必须登录的接口
 * if (UserContext.hasRole("HR_ADMIN")) { ... }   // 角色判断
 * </pre>
 */
public final class UserContext {

    /** 网关写入的用户 ID 头。值是用户中心的主键字符串。 */
    public static final String HEADER_USER_ID = "X-User-Uid";

    /** 网关写入的角色列表头。多个角色用英文逗号分隔，例如 {@code HR,INTERVIEWER}。 */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /**
     * 网关写入的显示姓名头。
     * HTTP Header 规范只保证 ASCII 安全，中文姓名直接放进去可能被容器或代理破坏，
     * 所以约定用 UTF-8 字节做 Base64 编码后再传输。
     */
    public static final String HEADER_USER_NAME_B64 = "X-User-Name-B64";

    /** 链路追踪 ID 头。网关生成，贯穿一次请求的所有服务日志。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 存放当前线程用户快照的容器。
     * 用 ThreadLocal 而不是静态变量，是为了让并发请求之间互不干扰。
     */
    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    /** 工具类不允许实例化。 */
    private UserContext() {
    }

    /**
     * 写入当前线程的用户上下文。
     *
     * <p>userId 为空时会调用 {@link #clear()}：这是一种防御性处理，
     * 避免“匿名请求覆盖不掉旧值”导致读到上一个请求的用户。</p>
     *
     * @param userId      用户 ID，为空则清空上下文
     * @param roles       角色集合，可为 null（按空集合处理）
     * @param displayName 显示姓名，可为 null
     * @param traceId     链路 ID，可为 null；为 null 时读取会回退到 TraceIdUtil
     */
    public static void set(String userId, Collection<String> roles, String displayName, String traceId) {
        if (userId == null || userId.isBlank()) {
            clear();
            return;
        }
        HOLDER.set(new Snapshot(userId, roles, displayName, traceId));
    }

    /**
     * 只写用户 ID 和角色的简化版本，主要用于单元测试或不需要姓名的内部调用。
     *
     * @param userId 用户 ID
     * @param roles  角色集合
     */
    public static void set(String userId, Collection<String> roles) {
        set(userId, roles, null, null);
    }

    /**
     * 读取当前用户 ID。
     *
     * @return 用户 ID；未认证或不在请求线程中时返回 null
     */
    public static String getUserId() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? null : snapshot.userId;
    }

    /**
     * 读取当前用户 ID；未认证时直接抛 401 业务异常。
     *
     * <p>为什么提供这个方法：必须登录的接口如果每个都写
     * {@code if (getUserId() == null) throw ...}，既啰嗦又容易漏判。
     * 统一在这里抛 {@link ErrorCode#GW_UNAUTHENTICATED}，由全局异常处理器转成 401。</p>
     *
     * @return 当前用户 ID，保证非空
     * @throws BizException 未获取到用户时抛出（GW9001 / HTTP 401）
     */
    public static String requireUserId() {
        String userId = getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED, "未获取到当前用户");
        }
        return userId;
    }

    /**
     * 读取当前用户角色集合。
     *
     * @return 只读角色集合；未认证时返回空集合而不是 null，调用方可以直接遍历
     */
    public static Set<String> getRoles() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? Collections.emptySet() : snapshot.roles;
    }

    /**
     * 判断当前用户是否拥有某个角色。
     *
     * @param role 角色编码，区分大小写；为 null 时返回 false
     * @return 是否拥有该角色
     */
    public static boolean hasRole(String role) {
        return role != null && getRoles().contains(role);
    }

    /**
     * 判断当前用户是否拥有任意一个指定角色（OR 语义）。
     *
     * @param roles 角色编码数组，可为 null
     * @return 命中任意一个即返回 true
     */
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

    /**
     * 读取当前用户显示姓名（已从 Base64 解码为普通字符串）。
     *
     * @return 显示姓名；未认证或网关没传时返回 null
     */
    public static String getDisplayName() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? null : snapshot.displayName;
    }

    /**
     * 读取链路 ID。
     *
     * <p>优先使用网关注入的值；如果当前不在请求线程（例如定时任务），
     * 回退到 {@link TraceIdUtil#get()}，保证日志里始终有一个可关联的 ID。</p>
     *
     * @return 链路 ID，可能为 null（TraceIdUtil 也没有时）
     */
    public static String getTraceId() {
        Snapshot snapshot = HOLDER.get();
        if (snapshot != null && snapshot.traceId != null) {
            return snapshot.traceId;
        }
        return TraceIdUtil.get();
    }

    /**
     * 当前线程是否已经认证（即有没有用户 ID）。
     *
     * @return true 表示已认证
     */
    public static boolean isAuthenticated() {
        return getUserId() != null;
    }

    /**
     * 清空当前线程的用户上下文。
     *
     * <p><b>必须在请求结束时调用</b>（过滤器 finally 块），否则 Tomcat 复用线程时
     * 下一个请求会读到上一个请求的用户，造成越权。调用 {@code remove()} 而不是
     * {@code set(null)}，可以真正释放 ThreadLocalMap 中的 Entry，避免内存泄漏。</p>
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 用户信息快照。
     *
     * <p>设计成不可变对象：请求处理过程中即使有人尝试修改角色集合，也不会影响上下文，
     * 避免“同一次请求里前后权限判断结果不一致”。</p>
     */
    private static final class Snapshot {

        private final String userId;

        /** 只读角色集合，保持网关传入的顺序（LinkedHashSet），便于日志排查。 */
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
