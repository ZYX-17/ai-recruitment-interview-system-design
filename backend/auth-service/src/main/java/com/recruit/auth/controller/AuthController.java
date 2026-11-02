package com.recruit.auth.controller;

import com.recruit.auth.config.SecurityProperties;
import com.recruit.auth.dto.LoginForm;
import com.recruit.auth.dto.LoginResult;
import com.recruit.auth.dto.RefreshForm;
import com.recruit.auth.dto.RefreshResult;
import com.recruit.auth.service.AuthService;
import com.recruit.auth.service.TokenService;
import com.recruit.auth.support.RefreshCookieFactory;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import jakarta.servlet.http.Cookie;
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

    /**
     * 刷新令牌（A-03）。
     *
     * <p>优先读 Refresh Cookie；仅 local 的响应体模式允许从 body 取。
     * 无效/重放时同时下发过期 Cookie，避免前端反复带坏 token。</p>
     *
     * @param form     可选 body（仅 local）
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @return 新 Access Token（生产不含 Refresh Token）
     */
    @PostMapping("/refresh")
    public Result<RefreshResult> refresh(@RequestBody(required = false) RefreshForm form,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        String token = readRefreshCookie(request);
        if (token == null && form != null && securityProperties.getRefreshCookie().isExposeInBody()) {
            token = form.getRefreshToken();
        }
        try {
            RefreshResult result = authService.refresh(token, request);
            response.addHeader(HttpHeaders.SET_COOKIE,
                    refreshCookieFactory.create(result.refreshToken(), tokenService.refreshTtlSeconds()).toString());
            return Result.ok(securityProperties.getRefreshCookie().isExposeInBody()
                    ? result
                    : result.withoutRefreshToken());
        } catch (BizException e) {
            if (e.getErrorCode() == ErrorCode.AUTH_REFRESH_INVALID
                    || e.getErrorCode() == ErrorCode.AUTH_REFRESH_REPLAYED) {
                response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString());
            }
            throw e;
        }
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = securityProperties.getRefreshCookie().getName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
