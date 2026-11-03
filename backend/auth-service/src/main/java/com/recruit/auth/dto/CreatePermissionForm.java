package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增权限入参（A-15）。
 *
 * <p>一个权限码一行：{@code type} 表示主要形态（MENU/BUTTON/API），
 * BUTTON 可同时携带 {@code apiPattern} 供接口校验，不要为同一动作重复建两行。</p>
 */
@Data
public class CreatePermissionForm {

    /** 权限码，全局唯一，如 interview:job:view。 */
    @NotBlank(message = "权限码不能为空")
    @Size(max = 128, message = "权限码长度不能超过 128")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9:._-]{1,127}",
            message = "权限码只能包含字母、数字、冒号、点、下划线与中划线")
    private String permissionCode;

    /** MENU / BUTTON / API。 */
    @NotBlank(message = "权限类型不能为空")
    @Pattern(regexp = "MENU|BUTTON|API", message = "type 只能是 MENU/BUTTON/API")
    private String type;

    /** 父权限 ID；只有 MENU 可以作为父级。 */
    private Long parentId;

    /** 权限名称。 */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64, message = "权限名称长度不能超过 64")
    private String name;

    /** MENU：前端路由。 */
    @Size(max = 255, message = "路由长度不能超过 255")
    private String routePath;

    /** MENU：页面组件路径。 */
    @Size(max = 255, message = "组件路径长度不能超过 255")
    private String component;

    /** MENU：前端图标标识。 */
    @Size(max = 64, message = "图标标识长度不能超过 64")
    private String icon;

    /** API/BUTTON：接口匹配模式。 */
    @Size(max = 255, message = "接口模式长度不能超过 255")
    private String apiPattern;

    /** 排序，默认 0。 */
    @Min(value = 0, message = "sort 不能为负数")
    private Integer sort;

    /** 状态，默认 ACTIVE。 */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能是 ACTIVE/DISABLED")
    private String status;
}
