package com.recruit.common.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 一次查询的数据范围决策，由 {@link DataScopeResolver} 解析后交给切面使用。
 *
 * @param type    范围类型
 * @param userId  当前用户 ID；ASSIGNED/SELF 必填
 * @param deptIds 当前用户可见的部门 ID 集合（含子部门）；DEPARTMENT 必填，其他类型可为空
 */
public record DataScopeDecision(DataScopeType type, String userId, Set<String> deptIds) {

    /**
     * 规范化输入：type 不能为空，deptIds 做防御性拷贝并保持顺序。
     *
     * @param type    范围类型
     * @param userId  用户 ID
     * @param deptIds 部门 ID 集合
     */
    public DataScopeDecision {
        Objects.requireNonNull(type, "数据范围类型不能为空");
        userId = userId == null || userId.isBlank() ? null : userId.trim();
        deptIds = deptIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(deptIds));
    }

    /**
     * 构造只有用户、没有部门列表的决策（ALL/ASSIGNED/SELF 常用）。
     *
     * @param type   范围类型
     * @param userId 用户 ID
     * @return 决策对象
     */
    public static DataScopeDecision of(DataScopeType type, String userId) {
        return new DataScopeDecision(type, userId, null);
    }

    /**
     * 构造部门范围决策。
     *
     * @param userId  用户 ID
     * @param deptIds 可见部门 ID
     * @return 决策对象
     */
    public static DataScopeDecision department(String userId, Collection<String> deptIds) {
        return new DataScopeDecision(DataScopeType.DEPARTMENT, userId,
                deptIds == null ? null : new LinkedHashSet<>(deptIds));
    }
}
