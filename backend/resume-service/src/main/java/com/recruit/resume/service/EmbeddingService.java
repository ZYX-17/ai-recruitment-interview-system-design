package com.recruit.resume.service;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.resume.client.AiWorkerClient;
import com.recruit.resume.client.dto.EmbedResponse;
import com.recruit.resume.support.LocalMockEmbedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 编排：批量调用 ai-worker，失败时可降级本地 mock。
 *
 * <p>批量 32~64 条，校验维度 512 与返回数量；降级只用于开发/演示，
 * 生产通过 {@code EMBED_FALLBACK_MOCK=false} 关闭，让任务明确失败并可重试。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final AiWorkerClient aiWorkerClient;

    @Value("${recruit.embed.dims:512}")
    private int dims;

    @Value("${recruit.embed.batch-size:32}")
    private int batchSize;

    @Value("${recruit.embed.fallback-mock:true}")
    private boolean fallbackMock;

    public EmbeddingResult embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResult(List.of(), "none", "none");
        }
        int size = Math.min(Math.max(batchSize, 1), 64);
        try {
            List<float[]> vectors = new ArrayList<>(texts.size());
            String model = "unknown";
            String mode = "remote";
            for (int start = 0; start < texts.size(); start += size) {
                List<String> batch = texts.subList(start, Math.min(start + size, texts.size()));
                EmbedResponse response = aiWorkerClient.embed(batch);
                if (response == null || response.embeddings() == null
                        || response.embeddings().size() != batch.size()) {
                    throw new IllegalStateException("embedding 返回数量不一致");
                }
                if (response.dims() != null && response.dims() != dims) {
                    throw new IllegalStateException("embedding 维度不是 " + dims);
                }
                for (List<Float> vector : response.embeddings()) {
                    if (vector == null || vector.size() != dims) {
                        throw new IllegalStateException("embedding 维度不是 " + dims);
                    }
                    float[] array = new float[dims];
                    for (int i = 0; i < dims; i++) {
                        array[i] = vector.get(i);
                    }
                    vectors.add(array);
                }
                model = response.model();
                mode = response.mode();
            }
            return new EmbeddingResult(vectors, model, mode);
        } catch (Exception e) {
            if (!fallbackMock) {
                throw new BizException(ErrorCode.EMBEDDING_FAILED, e);
            }
            log.warn("ai-worker embedding 失败，降级本地 mock：{}", e.getMessage());
            List<float[]> vectors = texts.stream()
                    .map(text -> LocalMockEmbedding.vector(text, dims))
                    .toList();
            return new EmbeddingResult(vectors, "mock-local", "mock");
        }
    }

    /**
     * @param vectors 与输入文本一一对应
     * @param model   实际模型标识
     * @param mode    remote / mock
     */
    public record EmbeddingResult(List<float[]> vectors, String model, String mode) {
    }
}
