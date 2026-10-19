package com.recruit.auth.controller;

import com.recruit.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 冒烟探针（骨架期）。W2 起替换为真实 IAM 端点（登录/令牌/用户/角色/菜单/权限）。
 */
@RestController
@RequestMapping("/api/auth")
public class PingController {

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "service", "auth-service",
                "port", 8081,
                "schema", "recruit_auth",
                "time", Instant.now().toString()));
    }
}
