package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.PortalAsyncSubmitForm;
import com.recruit.interview.dto.PortalAsyncView;
import com.recruit.interview.service.AsyncPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 候选人门户异步面试（免登录，网关白名单 {@code /portal/async-invites/**}）。
 *
 * <p>凭邀请 token 访问；开始/提交都幂等，重复提交返回 {@code AIS5102}。</p>
 */
@RestController
@RequestMapping("/api/interview/portal/async-invites")
@RequiredArgsConstructor
public class AsyncPortalController {

    private final AsyncPortalService service;

    /**
     * 查看试卷/进度。
     *
     * @param token 明文 token
     * @return 门户视图
     */
    @GetMapping("/{token}")
    public Result<PortalAsyncView> detail(@PathVariable String token) {
        return Result.ok(service.detail(token));
    }

    /**
     * 开始作答。
     *
     * @param token 明文 token
     * @param key   Idempotency-Key（可选）
     * @return 门户视图
     */
    @PostMapping("/{token}/start")
    public Result<PortalAsyncView> start(
            @PathVariable String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(service.start(token, key));
    }

    /**
     * 提交作答。
     *
     * @param token 明文 token
     * @param form  作答列表
     * @param key   Idempotency-Key（可选）
     * @return 提交后的门户视图
     */
    @PostMapping("/{token}/submit")
    public Result<PortalAsyncView> submit(
            @PathVariable String token,
            @Valid @RequestBody PortalAsyncSubmitForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(service.submit(token, form, key));
    }
}
