package com.recruit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增角色入参（A-12）。
 *
 * <p>{@code roleCode} 落库前统一转大写；{@code isSystemRole} 只有超级管理员可以设为 true。
 * 数据范围四选一，默认由前端显式传入，避免“漏传即全公司”。</p>
 */
@Data
public class CreateRoleForm {

    /** 角色编码，唯一；大写字母开头，只允许大写字母、数字与下划线。 */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码长度不能超过 64")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "角色编码只能包含字母、数字与下划线，且以字母开头")
    private String roleCode;

    /** 角色名。 */
    @NotBlank(message = "角色名不能为空")
    @Size(max = 64, message = "角色名长度不能超过 64")
    private String roleName;

    /** 数据范围：ALL / DEPARTMENT / ASSIGNED / SELF。 */
    @NotBlank(message = "数据范围不能为空")
    @Pattern(regexp = "ALL|DEPARTMENT|ASSIGNED|SELF", message = "dataScope 只能是 ALL/DEPARTMENT/ASSIGNED/SELF")
    private String dataScope;

    /** 是否系统内置角色；默认 false，仅超级管理员可设 true。 */
    private Boolean isSystemRole;

    /** 备注，可为空。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
