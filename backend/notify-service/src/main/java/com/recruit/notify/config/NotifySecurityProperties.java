package com.recruit.notify.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * notify-service 安全配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "recruit.security")
public class NotifySecurityProperties {

    /** 服务间内部接口令牌（调 auth-service 时携带）。 */
    private String internalToken = "";
}
