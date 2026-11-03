package com.recruit.auth.dto;

import java.util.List;

/**
 * 管理端权限树节点（A-15）。
 *
 * @param id          权限 ID（字符串）
 * @param parentId    父权限 ID（字符串），根节点为 null
 * @param code        权限码
 * @param name        权限名称
 * @param type        MENU / BUTTON / API
 * @param path        前端路由
 * @param component   页面组件路径
 * @param icon        前端图标标识
 * @param apiPattern  接口匹配模式
 * @param sort        排序
 * @param status      ACTIVE / DISABLED
 * @param version     乐观锁版本
 * @param children    子节点
 */
public record PermissionNodeView(
        String id,
        String parentId,
        String code,
        String name,
        String type,
        String path,
        String component,
        String icon,
        String apiPattern,
        int sort,
        String status,
        int version,
        List<PermissionNodeView> children) {

    public PermissionNodeView {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
