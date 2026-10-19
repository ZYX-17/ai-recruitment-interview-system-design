package com.recruit.resume.controller;

import com.recruit.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class PingController {

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "service", "resume-service",
                "port", 8084,
                "schema", "recruit_resume",
                "time", Instant.now().toString()));
    }
}
