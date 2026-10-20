package com.recruit.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 连接配置（对应 application.yml 的 {@code recruit.minio.*}）。
 *
 * <p>密钥只从环境变量注入，不写死在代码/仓库里；预签名默认 10 分钟，
 * 与开发文档“下载 URL 10min 有效”的契约一致。</p>
 */
@Data
@ConfigurationProperties(prefix = "recruit.minio")
public class MinioProperties {

    /** MinIO 服务地址，例如 http://127.0.0.1:9000。 */
    private String endpoint = "http://127.0.0.1:9000";

    /** 访问密钥；生产环境必须通过 MINIO_ACCESS_KEY 注入。 */
    private String accessKey = "minioadmin";

    /** 私钥；生产环境必须通过 MINIO_SECRET_KEY 注入。 */
    private String secretKey = "minioadmin";

    /** 统一桶名；B 的所有业务对象（简历、录像、凭证）都放这个桶。 */
    private String bucket = "recruit";

    /** 预签名 URL 有效期（分钟）。 */
    private int presignTtlMinutes = 10;

    /** 启动时是否自动建桶；MinIO 未启动时只告警，不阻塞服务启动。 */
    private boolean initBucket = true;
}
