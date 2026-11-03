package com.recruit.interview.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * interview-service 安全配置（docs/4 §3.3）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "recruit.security")
public class InterviewSecurityProperties {

    /**
     * 服务间内部接口令牌（INTERNAL_TOKEN）；未配置时 {@code /internal/**} 一律拒绝。
     */
    private String internalToken = "";
}
