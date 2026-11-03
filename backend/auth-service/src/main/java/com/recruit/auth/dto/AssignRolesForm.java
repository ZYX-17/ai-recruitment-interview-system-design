package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 分配角色入参（A-11）。
 *
 * <p>全量替换：{@code roleIds} 是替换后的完整角色集合，传空数组表示清空角色。
 * {@code version} 可选；传了就做乐观锁校验，冲突返回 {@code SYS412}。</p>
 */
@Data
public class AssignRolesForm {

    /** 替换后的角色 ID 集合，不能为 null；空数组表示清空。 */
    @NotNull(message = "roleIds 不能为空")
    @Size(max = 20, message = "角色数量不能超过 20")
    private List<Long> roleIds;

    /** 乐观锁版本号；为空表示不做版本校验。 */
    @Min(value = 0, message = "version 不能为负数")
    private Integer version;
}
