package com.recruit.file;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文件服务（Owner：B）。库：recruit_file；对象实体在 MinIO。
 * 核心：上传/下载、预签名 URL（10min）、MIME/大小/内容校验（sha256 秒传查重）。
 */
@EnableFeignClients
@EnableDiscoveryClient
@MapperScan("com.recruit.file.mapper")
@SpringBootApplication
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
