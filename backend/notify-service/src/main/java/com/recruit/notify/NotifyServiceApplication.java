package com.recruit.notify;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 通知服务（Owner：A）。库：recruit_notify。
 * 核心：notification.requested 事件消费 → 模板渲染 → 邮件（SMTP）/站内信发送，
 * 失败重试 + 发送日志；短信 Mock。验收口径：通知 60s 内达。
 */
@EnableFeignClients
@EnableDiscoveryClient
@MapperScan("com.recruit.notify.mapper")
@SpringBootApplication
public class NotifyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyServiceApplication.class, args);
    }
}
