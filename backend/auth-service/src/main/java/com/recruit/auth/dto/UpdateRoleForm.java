package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改角色入参（A-13）。
 *
 * <p>白名单字段：角色名、数据范围、状态、备注；{@code roleCode} 与 {@code isSystemRole} 不可改。
 * 系统内置角色的数据范围与状态由 seed 维护，本接口拒绝修改。</p>
 */
@Data
public class UpdateRoleForm {

    /** 角色名；为空表示不修改。 */
    @Size(max = 64, message = "角色名长度不能超过 64")
    private String roleName;

    /** 数据范围；为空表示不修改。 */
    @Pattern(regexp = "ALL|DEPARTMENT|ASSIGNED|SELF", message = "dataScope 只能是 ALL/DEPARTMENT/ASSIGNED/SELF")
    private String dataScope;

    /** 状态；为空表示不修改。 */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能是 ACTIVE/DISABLED")
    private String status;

    /** 备注；传空串表示清空，传 null 表示不修改。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;

    /** 乐观锁版本号，必填。 */
    @NotNull(message = "version 不能为空")
    @Min(value = 0, message = "version 不能为负数")
    private Integer version;
}
