package com.recruit.auth.dto;

import java.util.List;

/**
 * 动态菜单节点（A-06）。
 *
 * <p>只包含 MENU 类型且当前用户可见的节点；BUTTON/API 不进入菜单树。</p>
 *
 * @param id        菜单 ID（字符串）
 * @param parentId  父菜单 ID（字符串），根节点为 null
 * @param code      权限码
 * @param name      菜单名称
 * @param type      固定为 MENU
 * @param path      前端路由
 * @param component 页面组件路径
 * @param icon      前端图标标识
 * @param sort      排序
 * @param children  子菜单
 */
public record MenuNodeView(
        String id,
        String parentId,
        String code,
        String name,
        String type,
        String path,
        String component,
        String icon,
        int sort,
        List<MenuNodeView> children) {

    public MenuNodeView {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
