package com.recruit.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataScopeSqlBuilder} 的单元测试。
 *
 * <p>重点验证四种范围的 SQL 片段、参数顺序，以及“部门为空必须 fail-closed”。</p>
 */
class DataScopeSqlBuilderTest {

    /**
     * ALL 不产生任何条件，调用方不应追加 AND。
     */
    @Test
    void allScopeProducesEmptySql() {
        DataScopeSql sql = DataScopeSqlBuilder.build(
                DataScopeDecision.of(DataScopeType.ALL, "10001"),
                "dept_id", "owner_hr_id", "");

        assertTrue(sql.isEmpty());
        assertTrue(sql.params().isEmpty());
    }

    /**
     * DEPARTMENT 生成 IN 条件，参数顺序与部门集合一致。
     */
    @Test
    void departmentScopeBuildsInClause() {
        DataScopeSql sql = DataScopeSqlBuilder.build(
                DataScopeDecision.department("10001", List.of("10", "11")),
                "dept_id", "owner_hr_id", "");

        assertEquals("dept_id IN (?, ?)", sql.sql());
        assertEquals(List.of("10", "11"), sql.params());
    }

    /**
     * 部门列表为空时必须 1=0，不能退化成全量查询。
     */
    @Test
    void emptyDepartmentFailsClosed() {
        DataScopeSql sql = DataScopeSqlBuilder.build(
                DataScopeDecision.department("10001", List.of()),
                "dept_id", "owner_hr_id", "");

        assertEquals("1 = 0", sql.sql());
        assertTrue(sql.params().isEmpty());
    }

    /**
     * SELF 按负责人列精确匹配当前用户。
     */
    @Test
    void selfScopeMatchesOwner() {
        DataScopeSql sql = DataScopeSqlBuilder.build(
                DataScopeDecision.of(DataScopeType.SELF, "10001"),
                "dept_id", "owner_hr_id", "");

        assertEquals("owner_hr_id = ?", sql.sql());
        assertEquals(List.of("10001"), sql.params());
    }

    /**
     * ASSIGNED 配置了 JSON 列时，用 JSON_CONTAINS 判断数组包含当前用户。
     */
    @Test
    void assignedScopeUsesJsonContains() {
        DataScopeSql sql = DataScopeSqlBuilder.build(
                DataScopeDecision.of(DataScopeType.ASSIGNED, "10001"),
                "dept_id", "owner_hr_id", "interviewer_ids");

        assertEquals("JSON_CONTAINS(interviewer_ids, CAST(? AS JSON))", sql.sql());
        assertEquals(List.of("10001"), sql.params());
    }

    /**
     * ASSIGNED 未配置 JSON 列时回退到负责人列。
     */
    @Test
    void assignedScopeFallsBackToOwner() {
        DataScopeSql sql = DataScopeSqlBuilder.build(
                DataScopeDecision.of(DataScopeType.ASSIGNED, "10001"),
                "dept_id", "owner_hr_id", "");

        assertEquals("owner_hr_id = ?", sql.sql());
        assertEquals(List.of("10001"), sql.params());
    }

    /**
     * 列名非法（可能来自配置错误或注入尝试）必须直接失败。
     */
    @Test
    void rejectsInvalidColumnName() {
        assertThrows(IllegalArgumentException.class, () -> DataScopeSqlBuilder.build(
                DataScopeDecision.department("10001", List.of("10")),
                "dept_id; DROP TABLE x", "owner_hr_id", ""));
    }

    /**
     * SELF/ASSIGNED 缺少 userId 时不能生成空条件。
     */
    @Test
    void rejectsMissingUserIdForNonAllScope() {
        assertThrows(IllegalArgumentException.class, () -> DataScopeSqlBuilder.build(
                DataScopeDecision.of(DataScopeType.SELF, null),
                "dept_id", "owner_hr_id", ""));
    }
}
