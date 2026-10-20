package com.recruit.common.security;

import java.util.List;

/**
 * 数据范围过滤片段：一段 WHERE 条件 + 对应的绑定参数。
 *
 * <p>只包含条件本身（例如 {@code dept_id IN (?, ?)}），调用方负责在 SQL 里补
 * {@code AND (...)}。参数顺序与 SQL 中的 {@code ?} 一一对应，必须按顺序绑定。</p>
 *
 * @param sql    过滤条件；空字符串表示不过滤
 * @param params 绑定参数，顺序与占位符一致
 */
public record DataScopeSql(String sql, List<Object> params) {

    /** 空条件：ALL 范围使用。 */
    public static final DataScopeSql EMPTY = new DataScopeSql("", List.of());

    /**
     * 规范化参数列表，避免外部修改。
     *
     * @param sql    过滤条件
     * @param params 绑定参数
     */
    public DataScopeSql {
        sql = sql == null ? "" : sql;
        params = params == null ? List.of() : List.copyOf(params);
    }

    /**
     * 是否没有过滤条件。
     *
     * @return true 表示 ALL 范围
     */
    public boolean isEmpty() {
        return sql.isBlank();
    }
}
