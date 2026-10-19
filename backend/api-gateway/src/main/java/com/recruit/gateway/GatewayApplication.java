package com.recruit.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 统一网关（WebFlux）。职责：路由 / JWT 验签前置 / IP+用户令牌桶限流 /
 * /internal/** 外部屏蔽 / traceId 生成。媒体信令（LiveKit）与 ASR WSS 不经过本网关。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
