package com.recruit.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 刷新入参（仅 local 响应体模式使用；生产从 Cookie 取）。
 */
@Data
public class RefreshForm {

    @Size(max = 4096, message = "Refresh Token 长度不合法")
    private String refreshToken;
}
