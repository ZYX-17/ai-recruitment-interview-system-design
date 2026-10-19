package com.recruit.interview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 面试管理子系统（Owner：A）。库：recruit_interview。
 * 核心：流程模板（版本化）、候选人状态机（显式流转表+乐观锁）、排期三方冲突
 * （schedule_slot 唯一索引兜底 ITV3101）、评价（模板版本化）、Offer（confirm_token CAS）、AI 纪要存储。
 */
@EnableFeignClients
@EnableDiscoveryClient
@MapperScan("com.recruit.interview.mapper")
@SpringBootApplication
public class InterviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewServiceApplication.class, args);
    }
}
