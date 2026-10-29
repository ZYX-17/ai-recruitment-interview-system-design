package com.recruit.resume.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.resume.dto.ResumeSearchHitVO;
import com.recruit.resume.dto.ResumeSearchRequest;
import com.recruit.resume.dto.SearchFilters;
import com.recruit.resume.entity.Person;
import com.recruit.resume.entity.Resume;
import com.recruit.resume.entity.ResumeChunk;
import com.recruit.resume.mapper.PersonMapper;
import com.recruit.resume.mapper.ResumeChunkMapper;
import com.recruit.resume.mapper.ResumeMapper;
import com.recruit.resume.support.ResumeSearchMatcher;
import com.recruit.resume.support.ResumeStatus;
import com.recruit.resume.support.RrfFusion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 简历混合检索。
 *
 * <p>ES 路径：BM25 multi_match（content + section_path^2）与 kNN 各取 recallSize 个 chunk，
 * 用 RRF 融合后按 resume_id 聚合，再在应用层做 person 属性/技能过滤与分页。</p>
 *
 * <p>兜底路径：ES 不可用或 {@code SEARCH_MODE=mysql-like} 时，走 resume/person/resume_chunk
 * 的 SQL 过滤与 LIKE 匹配；两条路径共用 {@link ResumeSearchMatcher}，保证过滤口径一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingService embeddingService;
    private final ResumeMapper resumeMapper;
    private final PersonMapper personMapper;
    private final ResumeChunkMapper chunkMapper;

    @Value("${recruit.es.resume-index:resume_vec}")
    private String indexName;

    @Value("${recruit.search.mode:es}")
    private String searchMode;

    @Value("${recruit.search.fallback-to-mysql:false}")
    private boolean fallbackToMysql;

    @Value("${recruit.search.recall-size:200}")
    private int recallSize;

    @Value("${recruit.search.rrf-k:60}")
    private int rrfK;

    @Value("${recruit.search.max-candidates:200}")
    private int maxCandidates;

    public PageResult<ResumeSearchHitVO> search(ResumeSearchRequest request) {
        int pageNum = request.pageNum() == null ? 1 : Math.max(request.pageNum(), 1);
        int pageSize = request.pageSize() == null ? 20 : Math.min(Math.max(request.pageSize(), 1), 100);
        String keyword = request.keyword() == null ? "" : request.keyword().trim();
        SearchFilters filters = request.filters();

        if ("mysql-like".equalsIgnoreCase(searchMode) || keyword.isEmpty()) {
            return mysqlLike(keyword, filters, pageNum, pageSize);
        }
        try {
            return elasticSearch(keyword, filters, pageNum, pageSize);
        } catch (Exception e) {
            log.warn("ES 混合检索失败：{}", e.getMessage());
            if (fallbackToMysql) {
                return mysqlLike(keyword, filters, pageNum, pageSize);
            }
            throw new BizException(ErrorCode.SEARCH_ENGINE_UNAVAILABLE, e);
        }
    }

    private PageResult<ResumeSearchHitVO> elasticSearch(String keyword, SearchFilters filters,
                                                        int pageNum, int pageSize) throws Exception {
        List<ChunkHit> bm25 = bm25(keyword, recallSize);
        float[] queryVector = embeddingService.embed(List.of(keyword)).vectors().get(0);
        List<ChunkHit> knn = knn(queryVector, recallSize);

        Map<String, ChunkHit> byId = new HashMap<>();
        for (ChunkHit hit : bm25) {
            byId.put(hit.docId(), hit);
        }
        for (ChunkHit hit : knn) {
            byId.putIfAbsent(hit.docId(), hit);
        }

        Map<String, Double> fused = RrfFusion.fuse(ids(bm25), ids(knn), rrfK);
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : fused.entrySet()) {
            ChunkHit hit = byId.get(entry.getKey());
            if (hit == null) {
                continue;
            }
            long resumeId = asLong(hit.source().get("resume_id"));
            if (resumeId <= 0) {
                continue;
            }
            Candidate candidate = candidates.computeIfAbsent(resumeId, Candidate::new);
            candidate.score = Math.max(candidate.score, entry.getValue());
            if (candidate.highlights.size() < 2) {
                candidate.highlights.add(snippet(hit.source().get("content")));
            }
        }
        if (candidates.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        List<Map.Entry<Long, Candidate>> ranked = candidates.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().score, a.getValue().score))
                .limit(Math.max(maxCandidates, pageSize))
                .toList();
        Set<Long> resumeIds = ranked.stream().map(Map.Entry::getKey).collect(Collectors.toSet());
        Map<Long, Resume> resumes = loadResumes(resumeIds);
        Map<Long, Person> persons = loadPersons(resumes.values());
        Map<Long, String> contents = needsContent(filters)
                ? loadContents(resumeIds)
                : Map.of();

        List<ResumeSearchHitVO> hits = new ArrayList<>();
        for (Map.Entry<Long, Candidate> entry : ranked) {
            Resume resume = resumes.get(entry.getKey());
            if (resume == null) {
                continue;
            }
            Person person = persons.get(resume.getPersonId());
            if (!ResumeSearchMatcher.matches(resume, person, filters,
                    contents.getOrDefault(entry.getKey(), ""))) {
                continue;
            }
            Candidate candidate = entry.getValue();
            hits.add(ResumeSearchHitVO.from(resume, person, candidate.score, candidate.highlights));
        }
        return paginate(hits, pageNum, pageSize);
    }

    private List<ChunkHit> bm25(String keyword, int size) throws Exception {
        SearchResponse<Map> response = elasticsearchClient.search(search -> search
                .index(indexName)
                .size(size)
                .query(query -> query.bool(bool -> bool
                        .must(must -> must.multiMatch(multi -> multi
                                .query(keyword)
                                .fields("content", "section_path^2")))
                        .filter(filter -> filter.term(term -> term
                                .field("doc_type")
                                .value("resume"))))), Map.class);
        return toChunkHits(response);
    }

    private List<ChunkHit> knn(float[] queryVector, int size) throws Exception {
        SearchResponse<Map> response = elasticsearchClient.search(search -> search
                .index(indexName)
                .size(size)
                .knn(knn -> knn
                        .field("embedding")
                        .queryVector(toFloatList(queryVector))
                        .k(size)
                        .numCandidates(Math.max(size * 2L, 100L))
                        .filter(filter -> filter.term(term -> term
                                .field("doc_type")
                                .value("resume")))), Map.class);
        return toChunkHits(response);
    }

    @SuppressWarnings("unchecked")
    private List<ChunkHit> toChunkHits(SearchResponse<Map> response) {
        List<ChunkHit> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            if (hit.source() == null) {
                continue;
            }
            hits.add(new ChunkHit(hit.id(), hit.score() == null ? 0d : hit.score(),
                    (Map<String, Object>) hit.source()));
        }
        return hits;
    }

    /**
     * ES 不可用/未启用时的 SQL 兜底：先按状态取简历，再按 person 属性、技能、关键词过滤。
     */
    private PageResult<ResumeSearchHitVO> mysqlLike(String keyword, SearchFilters filters,
                                                    int pageNum, int pageSize) {
        String status = filters != null && StringUtils.hasText(filters.parseStatus())
                ? filters.parseStatus().trim()
                : ResumeStatus.SUCCESS;

        LambdaQueryWrapper<Resume> query = Wrappers.lambdaQuery();
        if (!"ALL".equalsIgnoreCase(status)) {
            query.eq(Resume::getParseStatus, status.toUpperCase(Locale.ROOT));
        }
        query.orderByDesc(Resume::getId);
        List<Resume> resumes = resumeMapper.selectList(query);
        if (resumes.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        Map<Long, Person> persons = loadPersons(resumes);
        Set<Long> resumeIds = resumes.stream().map(Resume::getId).collect(Collectors.toSet());
        Map<Long, List<ResumeChunk>> chunks = loadChunkGroups(resumeIds);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);

        List<ResumeSearchHitVO> hits = new ArrayList<>();
        for (Resume resume : resumes) {
            Person person = persons.get(resume.getPersonId());
            List<ResumeChunk> resumeChunks = chunks.getOrDefault(resume.getId(), List.of());
            String content = joinContents(resumeChunks);
            if (!ResumeSearchMatcher.matches(resume, person, filters, content)) {
                continue;
            }

            double score = 0;
            List<String> highlights = List.of();
            if (!keyword.isEmpty()) {
                List<ResumeChunk> matched = resumeChunks.stream()
                        .filter(chunk -> chunk.getContent() != null
                                && chunk.getContent().toLowerCase(Locale.ROOT).contains(lowerKeyword))
                        .toList();
                if (matched.isEmpty()) {
                    continue;
                }
                score = matched.size();
                highlights = matched.stream().limit(2).map(chunk -> snippet(chunk.getContent())).toList();
            }
            hits.add(ResumeSearchHitVO.from(resume, person, score, highlights));
        }

        hits.sort(Comparator.comparingDouble(ResumeSearchHitVO::score).reversed()
                .thenComparing(ResumeSearchHitVO::resumeId, Comparator.reverseOrder()));
        return paginate(hits, pageNum, pageSize);
    }

    private Map<Long, Resume> loadResumes(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return resumeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Resume::getId, resume -> resume));
    }

    private Map<Long, Person> loadPersons(Iterable<Resume> resumes) {
        Set<Long> personIds = new HashSet<>();
        for (Resume resume : resumes) {
            if (resume.getPersonId() != null) {
                personIds.add(resume.getPersonId());
            }
        }
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return personMapper.selectBatchIds(personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> person));
    }

    private Map<Long, String> loadContents(Set<Long> resumeIds) {
        Map<Long, List<ResumeChunk>> groups = loadChunkGroups(resumeIds);
        Map<Long, String> contents = new HashMap<>();
        groups.forEach((resumeId, chunks) -> contents.put(resumeId, joinContents(chunks)));
        return contents;
    }

    private Map<Long, List<ResumeChunk>> loadChunkGroups(Set<Long> resumeIds) {
        if (resumeIds.isEmpty()) {
            return Map.of();
        }
        List<ResumeChunk> chunks = chunkMapper.selectList(Wrappers.<ResumeChunk>lambdaQuery()
                .in(ResumeChunk::getResumeId, resumeIds));
        return chunks.stream().collect(Collectors.groupingBy(ResumeChunk::getResumeId));
    }

    private String joinContents(List<ResumeChunk> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ResumeChunk chunk : chunks) {
            if (chunk.getContent() != null) {
                builder.append(chunk.getContent()).append('\n');
            }
        }
        return builder.toString();
    }

    private PageResult<ResumeSearchHitVO> paginate(List<ResumeSearchHitVO> hits,
                                                   int pageNum, int pageSize) {
        long total = hits.size();
        int from = (int) Math.min((long) (pageNum - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        return PageResult.of(hits.subList(from, to), total, pageNum, pageSize);
    }

    private boolean needsContent(SearchFilters filters) {
        return filters != null && filters.skills() != null && !filters.skills().isEmpty();
    }

    private List<String> ids(List<ChunkHit> hits) {
        return hits.stream().map(ChunkHit::docId).toList();
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String snippet(Object content) {
        if (content == null) {
            return "";
        }
        String text = String.valueOf(content).replaceAll("\\s+", " ").trim();
        return text.length() <= 160 ? text : text.substring(0, 160) + "...";
    }

    private record ChunkHit(String docId, double score, Map<String, Object> source) {
    }

    private static final class Candidate {
        private final long resumeId;
        private double score;
        private final List<String> highlights = new ArrayList<>();

        private Candidate(long resumeId) {
            this.resumeId = resumeId;
        }
    }
}
