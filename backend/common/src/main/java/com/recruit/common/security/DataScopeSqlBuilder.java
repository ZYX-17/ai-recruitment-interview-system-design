package com.recruit.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 根据数据范围决策生成 WHERE 过滤片段。
 *
 * <p>只生成片段，不执行 SQL。列名必须来自注解常量，本类会用白名单正则校验，
 * 防止把请求参数拼进 SQL 造成注入。</p>
 */
public final class DataScopeSqlBuilder {

    /** 合法列名：字母/数字/下划线，且不能以数字开头。 */
    private static final Pattern COLUMN_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private DataScopeSqlBuilder() {
    }

    /**
     * 生成过滤片段。
     *
     * @param decision       数据范围决策
     * @param deptColumn     部门列名
     * @param ownerColumn    负责人列名
     * @param assignedColumn 分配关系列名；为空时 ASSIGNED 回退到 ownerColumn
     * @return 过滤条件与参数；ALL 返回 {@link DataScopeSql#EMPTY}
     */
    public static DataScopeSql build(DataScopeDecision decision, String deptColumn,
                                     String ownerColumn, String assignedColumn) {
        if (decision == null || decision.type() == null) {
            throw new IllegalArgumentException("数据范围决策不能为空");
        }
        return switch (decision.type()) {
            case ALL -> DataScopeSql.EMPTY;
            case DEPARTMENT -> buildDepartment(decision, deptColumn);
            case SELF -> buildSelf(decision, ownerColumn);
            case ASSIGNED -> buildAssigned(decision, ownerColumn, assignedColumn);
        };
    }

    /**
     * DEPARTMENT：dept_id IN (?, ?, ...)。
     *
     * <p>部门列表为空时生成 {@code 1 = 0}（fail-closed），
     * 绝不能因为“没查到部门”就退化成全量查询。</p>
     */
    private static DataScopeSql buildDepartment(DataScopeDecision decision, String deptColumn) {
        String column = requireColumn(deptColumn, "deptColumn");
        if (decision.deptIds().isEmpty()) {
            return new DataScopeSql("1 = 0", List.of());
        }
        List<Object> params = new ArrayList<>(decision.deptIds().size());
        StringBuilder sql = new StringBuilder(column).append(" IN (");
        int index = 0;
        for (String deptId : decision.deptIds()) {
            if (index++ > 0) {
                sql.append(", ");
            }
            sql.append('?');
            params.add(deptId);
        }
        sql.append(')');
        return new DataScopeSql(sql.toString(), params);
    }

    /**
     * SELF：owner_id = ?。
     */
    private static DataScopeSql buildSelf(DataScopeDecision decision, String ownerColumn) {
        String column = requireColumn(ownerColumn, "ownerColumn");
        String userId = requireUserId(decision, "SELF");
        return new DataScopeSql(column + " = ?", List.of(userId));
    }

    /**
     * ASSIGNED：优先按 JSON 数组列判断，未配置时回退到负责人列。
     *
     * <p>interviewer_ids 是 JSON 数组，使用
     * {@code JSON_CONTAINS(interviewer_ids, CAST(? AS JSON))}；
     * 参数按数字处理，和 DDL 里存的 BIGINT 数组一致。</p>
     */
    private static DataScopeSql buildAssigned(DataScopeDecision decision,
                                              String ownerColumn, String assignedColumn) {
        String userId = requireUserId(decision, "ASSIGNED");
        if (assignedColumn != null && !assignedColumn.isBlank()) {
            String column = requireColumn(assignedColumn, "assignedColumn");
            return new DataScopeSql("JSON_CONTAINS(" + column + ", CAST(? AS JSON))", List.of(userId));
        }
        String column = requireColumn(ownerColumn, "ownerColumn");
        return new DataScopeSql(column + " = ?", List.of(userId));
    }

    /**
     * 校验列名。
     *
     * @param column 列名
     * @param field  注解属性名，用于报错
     * @return 原列名
     */
    private static String requireColumn(String column, String field) {
        if (column == null || !COLUMN_PATTERN.matcher(column).matches()) {
            throw new IllegalArgumentException(field + " 不是合法列名: " + column);
        }
        return column;
    }

    /**
     * 非 ALL 范围必须有当前用户；缺失时直接失败，不能生成空条件。
     *
     * @param decision 决策
     * @param scope    范围名称，用于报错
     * @return 用户 ID
     */
    private static String requireUserId(DataScopeDecision decision, String scope) {
        String userId = decision.userId();
        if (userId == null) {
            throw new IllegalArgumentException(scope + " 范围缺少 userId");
        }
        return userId;
    }
}
