package com.recruit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 新增用户入参（A-08 / docs/4 §5.2.4）。
 *
 * <p>{@code initialPassword} 为空时由后端生成一次性临时密码，并在响应中返回；
 * 两种情况都会要求首次登录改密。{@code iam_user} 没有 email 列，因此这里不接收邮箱。</p>
 */
@Data
public class CreateUserForm {

    /** 登录名，唯一；只允许字母、数字与 _ . -。 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在 3~64 之间")
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "用户名只能包含字母、数字、下划线、点和中划线")
    private String username;

    /** 显示名。 */
    @NotBlank(message = "显示名不能为空")
    @Size(max = 64, message = "显示名长度不能超过 64")
    private String displayName;

    /** EMPLOYEE / CANDIDATE。 */
    @NotBlank(message = "用户类型不能为空")
    @Pattern(regexp = "EMPLOYEE|CANDIDATE", message = "userType 只能是 EMPLOYEE/CANDIDATE")
    private String userType;

    /** 部门 ID，可为空。 */
    private Long deptId;

    /** 手机号，可为空；落库前加密并写哈希盲索引。 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 初始角色 ID 列表，可为空。 */
    @Size(max = 20, message = "角色数量不能超过 20")
    private List<Long> roleIds;

    /** 初始状态，默认 ACTIVE。 */
    @Pattern(regexp = "ACTIVE|LOCKED|DISABLED", message = "status 只能是 ACTIVE/LOCKED/DISABLED")
    private String status;

    /** 管理员指定的初始密码；为空则后端生成，8~128 位。 */
    @Size(min = 8, max = 128, message = "初始密码长度需在 8~128 之间")
    private String initialPassword;
}
