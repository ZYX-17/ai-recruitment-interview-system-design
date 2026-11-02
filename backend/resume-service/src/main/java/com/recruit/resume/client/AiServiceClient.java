package com.recruit.resume.client;

import com.recruit.common.result.Result;
import com.recruit.common.trace.TraceIdUtil;
import com.recruit.resume.client.dto.ExtractResumeRequest;
import com.recruit.resume.client.dto.ExtractionResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * ai-service 客户端（简历实体抽取）。
 *
 * <p>AI 输出是草稿：调用失败不阻断索引，resume-service 留空 structuredJson，
 * 后续可通过重新抽取补偿。</p>
 */
@Component
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(@Qualifier("aiServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ExtractionResponse extractResume(long resumeId, String text) {
        Result<ExtractionResponse> result = restClient.post()
                .uri("/api/ai/v1/extract/resume")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    String traceId = TraceIdUtil.get();
                    if (StringUtils.hasText(traceId)) {
                        headers.set("X-Trace-Id", traceId);
                    }
                })
                .body(new ExtractResumeRequest(String.valueOf(resumeId), text))
                .retrieve()
                .body(new ParameterizedTypeReference<Result<ExtractionResponse>>() {
                });
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("AI 抽取失败：" +
                    (result == null ? "empty response" : result.getMessage()));
        }
        return result.getData();
    }
}
