package com.recruit.common.security;

/**
 * 数据范围解析器：由各业务服务实现，把“当前用户 + 资源”解析成具体范围。
 *
 * <p>为什么放在 common 而不是写死角色：数据范围来自 auth 服务的角色/部门配置，
 * 各服务不应重复维护角色到范围的映射。实现方可以查本地缓存或调用 auth 内部接口。</p>
 *
 * <p><b>实现要求：</b></p>
 * <ul>
 *   <li>不要信任请求参数里的 userId/deptId，只能使用传入的已认证 userId；</li>
 *   <li>解析失败时不要返回 {@link DataScopeType#ALL}，应抛异常（fail-closed）；</li>
 *   <li>DEPARTMENT 必须返回包含子部门的部门 ID 集合，不能只返回当前部门。</li>
 * </ul>
 */
@FunctionalInterface
public interface DataScopeResolver {

    /**
     * 解析数据范围。
     *
     * @param resource 资源名，例如 application
     * @param userId   当前登录用户 ID（来自网关可信头）
     * @return 数据范围决策，不能为 null
     */
    DataScopeDecision resolve(String resource, String userId);
}
