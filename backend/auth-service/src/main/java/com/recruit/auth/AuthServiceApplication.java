package com.recruit.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 身份与权限中心（IAM）。库：recruit_auth。
 * 核心：登录/双令牌（Access 2h + Refresh 7d + family 重放检测）、用户、角色、
 * 动态菜单/按钮/API 权限码、数据范围、操作审计。
 */
@EnableFeignClients
@EnableDiscoveryClient
@MapperScan("com.recruit.auth.mapper")
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
