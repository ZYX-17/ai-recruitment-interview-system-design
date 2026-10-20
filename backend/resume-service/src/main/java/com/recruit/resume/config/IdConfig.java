package com.recruit.resume.config;

import com.recruit.common.id.IdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 生成器。库表主键为 BIGINT UNSIGNED 且无 AUTO_INCREMENT，
 * 由应用层在插入前生成；对外 DTO 一律转字符串。
 *
 * <p>多实例部署时 worker-id / datacenter-id 必须各不相同（0~31），
 * 本地默认都用 1，单机开发足够。</p>
 */
@Configuration
public class IdConfig {

    @Bean
    public IdWorker idWorker(@Value("${recruit.id.worker-id:1}") long workerId,
                             @Value("${recruit.id.datacenter-id:1}") long datacenterId) {
        return new IdWorker(workerId, datacenterId);
    }
}
