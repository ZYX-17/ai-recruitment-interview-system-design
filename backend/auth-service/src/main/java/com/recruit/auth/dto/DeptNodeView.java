package com.recruit.auth.dto;

import java.util.List;

/**
 * 部门树节点（A-15 扩展，前端组织树 + 数据范围选择）。
 *
 * @param id       部门 ID
 * @param parentId 父部门 ID；根节点为 null
 * @param deptName 部门名称
 * @param sort     排序
 * @param status   ACTIVE / DISABLED
 * @param version  乐观锁版本
 * @param children 子部门
 */
public record DeptNodeView(String id,
                           String parentId,
                           String deptName,
                           int sort,
                           String status,
                           int version,
                           List<DeptNodeView> children) {
}
