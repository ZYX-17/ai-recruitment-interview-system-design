package com.recruit.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端与桶初始化。
 *
 * <p>桶初始化采用“启动时尝试、失败只告警”的策略：本地最小档（仅 MySQL）不启 MinIO 时，
 * 服务仍应能启动并暴露健康检查；真正调用文件接口时再由 Service 抛 FIL7006。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    /**
     * 启动后确保业务桶存在。MinIO 客户端本身是懒连接，这里才真正发起第一次请求。
     */
    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient, MinioProperties properties) {
        return args -> {
            if (!properties.isInitBucket()) {
                return;
            }
            String bucket = properties.getBucket();
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("MinIO 桶已创建：{}", bucket);
                }
            } catch (Exception e) {
                log.warn("MinIO 桶初始化失败（服务继续启动，文件接口将返回 FIL7006）：{}", e.getMessage());
            }
        };
    }
}
