package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改权限入参（A-15，PUT 全量语义）。
 *
 * <p>权限码与类型不可改；其余字段按提交值覆盖，传 null 表示清空（{@code parentId}=null 表示移到根节点）。
 * 菜单路径/图标变更后，前端动态路由会在下一次取菜单时更新。</p>
 */
@Data
public class UpdatePermissionForm {

    /** 权限名称；为空表示不修改。 */
    @Size(max = 64, message = "权限名称长度不能超过 64")
    private String name;

    /** 父权限 ID；null 表示根节点。 */
    private Long parentId;

    /** MENU：前端路由；null 清空。 */
    @Size(max = 255, message = "路由长度不能超过 255")
    private String routePath;

    /** MENU：页面组件路径；null 清空。 */
    @Size(max = 255, message = "组件路径长度不能超过 255")
    private String component;

    /** MENU：前端图标标识；null 清空。 */
    @Size(max = 64, message = "图标标识长度不能超过 64")
    private String icon;

    /** API/BUTTON：接口匹配模式；null 清空。 */
    @Size(max = 255, message = "接口模式长度不能超过 255")
    private String apiPattern;

    /** 排序；为空表示不修改。 */
    @Min(value = 0, message = "sort 不能为负数")
    private Integer sort;

    /** 状态；为空表示不修改。 */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能是 ACTIVE/DISABLED")
    private String status;

    /** 乐观锁版本号，必填。 */
    @NotNull(message = "version 不能为空")
    @Min(value = 0, message = "version 不能为负数")
    private Integer version;
}
