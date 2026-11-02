package com.recruit.resume.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 外部 HTTP 客户端。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient aiWorkerRestClient(
            @Value("${recruit.ai-worker.url:http://127.0.0.1:8001}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * ai-service：抽取可能走云端/本地 LLM，读超时放宽到 60 秒。
     */
    @Bean
    public RestClient aiServiceRestClient(
            @Value("${recruit.ai-service.url:http://127.0.0.1:9010}") String baseUrl,
            @Value("${recruit.internal.token:dev-internal-token}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(60000);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestFactory(factory)
                .build();
    }
}
