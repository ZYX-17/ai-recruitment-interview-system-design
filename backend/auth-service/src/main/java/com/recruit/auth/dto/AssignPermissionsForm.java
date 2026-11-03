package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 角色分配权限/菜单入参（A-14）。
 *
 * <p>全量替换：{@code permissionIds} 是替换后的完整集合，空数组表示清空。
 * {@code version} 可选；传了就做乐观锁校验，冲突返回 {@code SYS412}。</p>
 */
@Data
public class AssignPermissionsForm {

    /** 替换后的权限 ID 集合，不能为 null；空数组表示清空。 */
    @NotNull(message = "permissionIds 不能为空")
    @Size(max = 500, message = "权限数量不能超过 500")
    private List<Long> permissionIds;

    /** 乐观锁版本号；为空表示不做版本校验。 */
    @Min(value = 0, message = "version 不能为负数")
    private Integer version;
}
