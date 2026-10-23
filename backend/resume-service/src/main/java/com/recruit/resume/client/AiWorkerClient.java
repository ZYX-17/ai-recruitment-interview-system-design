package com.recruit.resume.client;

import com.recruit.resume.client.dto.EmbedRequest;
import com.recruit.resume.client.dto.EmbedResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * ai-worker HTTP 客户端（BGE embedding）。
 *
 * <p>ai-worker 不注册 Nacos，按固定地址访问；地址由 {@code recruit.ai-worker.url} 配置。</p>
 */
@Component
public class AiWorkerClient {

    private final RestClient restClient;

    public AiWorkerClient(@Qualifier("aiWorkerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public EmbedResponse embed(List<String> texts) {
        return restClient.post()
                .uri("/api/v1/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmbedRequest(texts))
                .retrieve()
                .body(EmbedResponse.class);
    }
}
