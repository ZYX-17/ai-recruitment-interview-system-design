package com.recruit.room.controller;

import com.recruit.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/room")
public class PingController {

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "service", "room-service",
                "port", 8085,
                "schema", "recruit_room",
                "time", Instant.now().toString()));
    }
}
