package com.recruit.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限（菜单/按钮/API 一行一码）。
 *
 * <p>对应 Flyway {@code V1_0__init.sql} 的 {@code iam_permission}，字段与列一一对应；
 * 枚举列用代码字符串（不做 MySQL ENUM），时间列为 UTC {@code DATETIME(3)}。</p>
 */
@Data
@TableName("iam_permission")
public class IamPermission {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 权限码全集见 docs/05-design/p0-5-permission-registry.csv。 */
    private String permissionCode;

    /** MENU / BUTTON / API。 */
    private String type;

    private Long parentId;
    private String name;

    /** MENU：前端路由。 */
    private String routePath;

    /** MENU：页面组件路径。 */
    private String component;

    /** MENU：前端图标标识。 */
    private String icon;

    /** API：接口匹配模式。 */
    private String apiPattern;

    private Integer sort;
    private String status;

    /** 乐观锁版本（A-15 修改权限使用）。 */
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
