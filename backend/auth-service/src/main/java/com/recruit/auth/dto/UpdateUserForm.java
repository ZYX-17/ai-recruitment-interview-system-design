package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户入参（A-09）。
 *
 * <p>只允许白名单字段：显示名、部门、状态；登录名与密码不可通过本接口修改。
 * {@code version} 为乐观锁版本号，冲突返回 {@code SYS412}。</p>
 */
@Data
public class UpdateUserForm {

    /** 显示名；为空表示不修改。 */
    @Size(max = 64, message = "显示名长度不能超过 64")
    private String displayName;

    /** 部门 ID；为空表示不修改。 */
    private Long deptId;

    /** 状态；为空表示不修改。 */
    @Pattern(regexp = "ACTIVE|LOCKED|DISABLED", message = "status 只能是 ACTIVE/LOCKED/DISABLED")
    private String status;

    /** 乐观锁版本号，必填。 */
    @NotNull(message = "version 不能为空")
    @Min(value = 0, message = "version 不能为负数")
    private Integer version;
}
