package com.recruit.common.security;

/**
 * 数据范围类型（与开发文档 §7.3.1 一致）。
 */
public enum DataScopeType {

    /** 全部数据：不追加过滤条件。仅超级管理员/HR_ADMIN 等明确角色可用。 */
    ALL,

    /** 本部门及子部门：按部门列过滤。 */
    DEPARTMENT,

    /** 仅与我相关：面试官看被分配的场次，按负责人或分配关系列过滤。 */
    ASSIGNED,

    /** 仅本人：候选人看自己的投递/Offer，按 owner 列过滤。 */
    SELF
}
