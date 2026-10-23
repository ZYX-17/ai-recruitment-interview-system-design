package com.recruit.resume;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能简历与人才管理子系统（Owner：B）。库：recruit_resume。
 * 核心：简历接入→MinerU 解析→分块→BGE→ES 索引（异步流水线）、向量查重
 * （sha256/phone_hash/kNN 0.92）、标签、硬过滤规则、双路匹配（0.3 召回+0.7 LLM）、人才库。
 */
@EnableFeignClients
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.recruit.resume.mapper")
@SpringBootApplication
public class ResumeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeServiceApplication.class, args);
    }
}
