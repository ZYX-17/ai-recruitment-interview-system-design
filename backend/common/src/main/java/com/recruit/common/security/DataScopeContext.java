package com.recruit.common.security;

/**
 * 当前线程的数据范围上下文。
 *
 * <p>切面在方法执行前写入，MyBatis 拦截器读取并改写 SQL，方法结束后清理。
 * 和 {@link com.recruit.common.context.UserContext} 一样，必须在线程复用前清理。</p>
 */
public final class DataScopeContext {

    private static final ThreadLocal<Snapshot> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    /**
     * 写入本次查询的数据范围与 SQL 片段。
     *
     * @param decision 数据范围决策，不能为空
     * @param sql      过滤片段，不能为空
     */
    public static void set(DataScopeDecision decision, DataScopeSql sql) {
        if (decision == null || sql == null) {
            throw new IllegalArgumentException("数据范围决策与 SQL 片段不能为空");
        }
        HOLDER.set(new Snapshot(decision, sql));
    }

    /**
     * 读取当前数据范围决策。
     *
     * @return 决策；不在 @DataScope 方法内时返回 null
     */
    public static DataScopeDecision decision() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? null : snapshot.decision;
    }

    /**
     * 读取当前过滤片段。
     *
     * @return 过滤片段；不在 @DataScope 方法内时返回 {@link DataScopeSql#EMPTY}
     */
    public static DataScopeSql sql() {
        Snapshot snapshot = HOLDER.get();
        return snapshot == null ? DataScopeSql.EMPTY : snapshot.sql;
    }

    /**
     * 当前是否处于数据范围方法内。
     *
     * @return true 表示有上下文
     */
    public static boolean isActive() {
        return HOLDER.get() != null;
    }

    /**
     * 清理上下文。必须在 finally 中调用，防止线程池复用导致不同请求串用过滤条件。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 上下文快照，不可变。
     *
     * @param decision 决策
     * @param sql      SQL 片段
     */
    private record Snapshot(DataScopeDecision decision, DataScopeSql sql) {
    }
}
