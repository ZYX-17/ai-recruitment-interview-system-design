package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/interview")
public class PingController {

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "service", "interview-service",
                "port", 8083,
                "schema", "recruit_interview",
                "time", Instant.now().toString()));
    }
}
