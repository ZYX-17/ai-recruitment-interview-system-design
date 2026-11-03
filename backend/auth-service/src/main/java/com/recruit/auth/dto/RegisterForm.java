package com.recruit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 候选人自助注册入参（A-02）。
 *
 * <p>只允许注册 {@code CANDIDATE}：员工账号仍由管理员创建。{@code iam_user}
 * 没有 email 列，因此这里不接收邮箱；候选人邮箱待简历域/通知域补齐。</p>
 */
@Data
public class RegisterForm {

    /** 登录名，唯一；只允许字母、数字与 _ . -。 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在 3~64 之间")
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "用户名只能包含字母、数字、下划线、点和中划线")
    private String username;

    /** 明文密码，服务端只存 BCrypt。 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度需在 8~128 之间")
    private String password;

    /** 显示姓名。 */
    @NotBlank(message = "显示名不能为空")
    @Size(max = 64, message = "显示名不能超过 64")
    private String displayName;

    /** 手机号，可选；落库前加密并写哈希查重。 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
