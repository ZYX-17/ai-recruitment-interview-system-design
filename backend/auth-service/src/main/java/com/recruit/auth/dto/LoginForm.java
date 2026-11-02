package com.recruit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录入参（A-01）。
 */
@Data
public class LoginForm {

    /** 登录名，大小写不敏感（库排序规则为 *_ai_ci）。 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    /** 明文密码；只在本次请求内使用，不落日志、不入审计。 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过 128")
    private String password;

    /** 可选：EMPLOYEE / CANDIDATE，用于拒绝跨端登录；为空不校验。 */
    @Size(max = 16, message = "用户类型长度不能超过 16")
    private String userType;
}
