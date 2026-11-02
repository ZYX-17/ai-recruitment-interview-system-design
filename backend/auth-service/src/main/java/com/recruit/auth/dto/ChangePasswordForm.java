package com.recruit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自助改密入参（P1-5）。
 *
 * <p>改密成功后本用户全部会话失效，前端必须清空令牌并重新登录。</p>
 */
@Data
public class ChangePasswordForm {

    /** 原密码，仅用于本次校验，不落日志、不入审计。 */
    @NotBlank(message = "原密码不能为空")
    @Size(max = 128, message = "原密码长度不能超过 128")
    private String oldPassword;

    /** 新密码；最短 8 位，避免弱口令。 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "新密码长度需在 8~128 之间")
    private String newPassword;
}
