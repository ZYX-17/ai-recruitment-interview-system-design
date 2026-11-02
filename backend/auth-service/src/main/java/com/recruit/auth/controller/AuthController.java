package com.recruit.auth.controller;

import com.recruit.auth.config.SecurityProperties;
import com.recruit.auth.dto.LoginForm;
import com.recruit.auth.dto.LoginResult;
import com.recruit.auth.service.AuthService;
import com.recruit.auth.service.TokenService;
import com.recruit.auth.support.RefreshCookieFactory;
import com.recruit.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证端点（A-01 起）。
 *
 * <p>Refresh Token 默认只写 Cookie；仅 local 的 {@code expose-in-body=true}
 * 时才额外放响应体，方便 Postman/联调。</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final SecurityProperties securityProperties;
    private final TokenService tokenService;

    /**
     * 登录。
     *
     * @param form     登录入参
     * @param request  HTTP 请求
     * @param response HTTP 响应（写 Set-Cookie）
     * @return 统一响应
     */
    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginForm form,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        LoginResult result = authService.login(form, request);
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookieFactory.create(result.refreshToken(), tokenService.refreshTtlSeconds()).toString());
        return Result.ok(securityProperties.getRefreshCookie().isExposeInBody()
                ? result
                : result.withoutRefreshToken());
    }
}
