package com.recruit.resume.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.resume.support.ResumeChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ES 索引维护：resume_vec（dense_vector 512 + BM25 文本字段）。
 *
 * <p>启动时尝试建索引；IK 分词器未安装时自动回退 standard，保证本地 ES 也能跑通。
 * 写入按 resume_id 先删后插，避免旧版本块残留。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeEsService {

    private final ElasticsearchClient elasticsearchClient;

    @Value("${recruit.es.resume-index:resume_vec}")
    private String indexName;

    @Value("${recruit.es.vector-dims:512}")
    private int dims;

    @Value("${recruit.es.analyzer:ik_max_word}")
    private String analyzer;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            ensureIndex();
        } catch (Exception e) {
            log.warn("ES 索引初始化失败（ES 不可用时检索降级）：{}", e.getMessage());
        }
    }

    public void ensureIndex() throws Exception {
        if (indexExists()) {
            return;
        }
        try {
            createIndex(analyzer);
        } catch (Exception e) {
            if (indexExists()) {
                return;
            }
            if (!"standard".equals(analyzer)) {
                log.warn("analyzer={} 创建索引失败，回退 standard：{}", analyzer, e.getMessage());
                createIndex("standard");
            } else {
                throw e;
            }
        }
    }

    private boolean indexExists() throws Exception {
        return elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
    }

    private void createIndex(String analyzerName) throws Exception {
        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(indexName)
                .settings(settings -> settings.numberOfShards("1").numberOfReplicas("0"))
                .mappings(mapping -> mapping
                        .properties("doc_id", property -> property.keyword(keyword -> keyword))
                        .properties("doc_type", property -> property.keyword(keyword -> keyword))
                        .properties("resume_id", property -> property.keyword(keyword -> keyword))
                        .properties("person_id", property -> property.keyword(keyword -> keyword))
                        .properties("chunk_no", property -> property.integer(integer -> integer))
                        .properties("chunk_type", property -> property.keyword(keyword -> keyword))
                        .properties("section_path", property -> property.text(text -> text.analyzer(analyzerName)))
                        .properties("content", property -> property.text(text -> text.analyzer(analyzerName)))
                        .properties("embedding", property -> property.denseVector(vector -> vector
                                .dims(dims)
                                .index(true)
                                .similarity("cosine")
                                .indexOptions(options -> options.type("hnsw").m(16).efConstruction(200))))
                        .properties("embedding_model", property -> property.keyword(keyword -> keyword))
                        .properties("updated_at", property -> property.date(date -> date))));
        elasticsearchClient.indices().create(request);
        log.info("ES 索引已创建 index={}, analyzer={}", indexName, analyzerName);
    }

    /**
     * 全量重建一份简历的检索文档。
     *
     * @param chunks  分块结果
     * @param vectors 与 chunks 一一对应的 512 维向量
     * @param model   embedding 模型标识
     */
    public void indexChunks(long resumeId, long personId,
                            List<ResumeChunker.Chunk> chunks, List<float[]> vectors, String model) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("chunks 与 vectors 数量不一致");
        }
        try {
            if (!indexExists()) {
                ensureIndex();
            }
            elasticsearchClient.deleteByQuery(query -> query
                    .index(indexName)
                    .query(term -> term.term(t -> t.field("resume_id").value(String.valueOf(resumeId)))));

            if (chunks.isEmpty()) {
                return;
            }
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (int i = 0; i < chunks.size(); i++) {
                ResumeChunker.Chunk chunk = chunks.get(i);
                String docId = resumeId + "-" + i;
                Map<String, Object> document = new LinkedHashMap<>();
                document.put("doc_id", docId);
                document.put("doc_type", "resume");
                document.put("resume_id", String.valueOf(resumeId));
                document.put("person_id", String.valueOf(personId));
                document.put("chunk_no", i);
                document.put("chunk_type", chunk.chunkType());
                document.put("section_path", chunk.sectionPath());
                document.put("content", chunk.content());
                document.put("embedding", toList(vectors.get(i)));
                document.put("embedding_model", model);
                document.put("updated_at", Instant.now().toString());
                bulk.operations(operation -> operation
                        .index(index -> index.index(indexName).id(docId).document(document)));
            }
            BulkResponse response = elasticsearchClient.bulk(bulk.build());
            if (response.errors()) {
                String reason = response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.error().reason())
                        .findFirst()
                        .orElse("unknown");
                throw new BizException(ErrorCode.SEARCH_ENGINE_UNAVAILABLE, "ES bulk 失败：" + reason);
            }
            log.info("ES 索引完成 resumeId={}, chunks={}, model={}", resumeId, chunks.size(), model);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SEARCH_ENGINE_UNAVAILABLE, e);
        }
    }

    private List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
