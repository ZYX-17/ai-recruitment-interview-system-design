package com.recruit.resume.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.id.IdWorker;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import com.recruit.contracts.event.EventEnvelope;
import com.recruit.contracts.event.EventTypes;
import com.recruit.resume.client.FileClient;
import com.recruit.resume.client.dto.DownloadUrlDTO;
import com.recruit.resume.client.dto.InternalPresignDownloadRequest;
import com.recruit.resume.entity.Resume;
import com.recruit.resume.entity.ResumeChunk;
import com.recruit.resume.entity.ResumeParseTask;
import com.recruit.resume.mapper.ResumeChunkMapper;
import com.recruit.resume.mapper.ResumeMapper;
import com.recruit.resume.mapper.ResumeParseTaskMapper;
import com.recruit.resume.support.ParseTaskStatus;
import com.recruit.resume.support.ResumeChunker;
import com.recruit.resume.support.ResumeStatus;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析完成后的索引链路：下载 Markdown → 分块 → BGE embedding → ES bulk → resume_chunk。
 *
 * <p>由 MQ 的 {@code resume.parse.completed} 触发，状态：STRUCTURING → INDEXING → SUCCESS。
 * 任一步失败置 FAILED 并发布 {@code resume.parse.failed}，由人工重试（≤3 次）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeIndexingService {

    private final ResumeMapper resumeMapper;
    private final ResumeParseTaskMapper taskMapper;
    private final ResumeChunkMapper chunkMapper;
    private final FileClient fileClient;
    private final EmbeddingService embeddingService;
    private final ResumeEsService resumeEsService;
    private final OutboxService outboxService;
    private final IdWorker idWorker;
    private final ObjectMapper objectMapper;

    public void handle(String body) {
        EventEnvelope<JsonNode> envelope = parse(body);
        if (envelope == null || !EventTypes.RESUME_PARSE_COMPLETED.equals(envelope.getEventType())) {
            return;
        }
        JsonNode payload = envelope.getPayload();
        long resumeId = longValue(payload, "resumeId");
        if (resumeId <= 0) {
            log.warn("resume.parse.completed 缺少 resumeId，忽略");
            return;
        }
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            log.warn("resume.parse.completed 对应简历不存在 resumeId={}", resumeId);
            return;
        }

        String taskNo = textOrNull(payload, "taskNo");
        Long markdownFileId = longOrNull(payload, "markdownFileId");
        if (markdownFileId == null) {
            markdownFileId = resume.getMarkdownFileId();
        }
        if (markdownFileId == null) {
            fail(resume, taskNo, "缺少 Markdown 产物");
            return;
        }

        try {
            updateResumeStatus(resume, ResumeStatus.INDEXING);
            updateTask(resumeId, taskNo, ParseTaskStatus.RUNNING, "INDEX", 30, null);

            String markdown = downloadMarkdown(markdownFileId);
            List<ResumeChunker.Chunk> chunks = ResumeChunker.chunk(markdown);
            if (chunks.isEmpty()) {
                throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "分块结果为空");
            }

            List<String> contents = chunks.stream().map(ResumeChunker.Chunk::content).toList();
            EmbeddingService.EmbeddingResult embedding = embeddingService.embed(contents);
            resumeEsService.indexChunks(resumeId, resume.getPersonId(), chunks,
                    embedding.vectors(), embedding.model());

            replaceChunks(resumeId, chunks, embedding.model());
            updateResumeStatus(resume, ResumeStatus.SUCCESS);
            updateTask(resumeId, taskNo, ParseTaskStatus.SUCCESS, "INDEX", 100, null);
            log.info("简历索引完成 resumeId={}, chunks={}, embeddingMode={}",
                    resumeId, chunks.size(), embedding.mode());
        } catch (Exception e) {
            log.error("简历索引失败 resumeId={}, taskNo={}", resumeId, taskNo, e);
            fail(resume, taskNo, e.getMessage());
        }
    }

    private void replaceChunks(long resumeId, List<ResumeChunker.Chunk> chunks, String embeddingModel) {
        chunkMapper.delete(Wrappers.<ResumeChunk>lambdaQuery()
                .eq(ResumeChunk::getResumeId, resumeId));
        for (int i = 0; i < chunks.size(); i++) {
            ResumeChunker.Chunk chunk = chunks.get(i);
            ResumeChunk entity = new ResumeChunk();
            entity.setId(idWorker.nextId());
            entity.setResumeId(resumeId);
            entity.setChunkNo(i);
            entity.setChunkType(chunk.chunkType());
            entity.setSectionPath(chunk.sectionPath());
            entity.setContent(chunk.content());
            entity.setEsDocId(resumeId + "-" + i);
            entity.setEmbeddingModel(embeddingModel);
            chunkMapper.insert(entity);
        }
    }

    private String downloadMarkdown(long fileId) {
        try {
            Result<DownloadUrlDTO> result = fileClient.presignDownload(
                    new InternalPresignDownloadRequest(fileId));
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new BizException(ErrorCode.FILE_STORAGE_ERROR, "获取 Markdown 下载地址失败");
            }
            URI uri = URI.create(result.getData().url());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BizException(ErrorCode.FILE_STORAGE_ERROR,
                        "下载 Markdown 失败 HTTP " + response.statusCode());
            }
            return response.body();
        } catch (FeignException e) {
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR, e);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    private void fail(Resume resume, String taskNo, String reason) {
        String message = reason == null || reason.isBlank() ? "索引失败" : truncate(reason, 512);
        updateResumeStatus(resume, ResumeStatus.FAILED);
        ResumeParseTask task = findTask(resume.getId(), taskNo);
        if (task != null) {
            int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            String status = retryCount >= 3 ? ParseTaskStatus.DEAD : ParseTaskStatus.FAILED;
            int progress = task.getProgress() == null ? 0 : task.getProgress();
            updateTask(resume.getId(), taskNo, status, task.getStage(), progress, message);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resumeId", String.valueOf(resume.getId()));
        payload.put("personId", String.valueOf(resume.getPersonId()));
        payload.put("taskNo", taskNo);
        payload.put("reason", message);
        payload.put("attempt", task == null ? 1 : (task.getRetryCount() == null ? 1 : task.getRetryCount() + 1));
        outboxService.append(EventTypes.RESUME_PARSE_FAILED, String.valueOf(resume.getId()), "RESUME", payload);
    }

    private void updateResumeStatus(Resume resume, String status) {
        // 状态机内部更新用条件更新，避免与其它字段的乐观锁版本互相干扰
        resumeMapper.update(null, Wrappers.<Resume>lambdaUpdate()
                .eq(Resume::getId, resume.getId())
                .set(Resume::getParseStatus, status));
    }

    private void updateTask(long resumeId, String taskNo, String status, String stage,
                            int progress, String errorMsg) {
        if (taskNo == null || taskNo.isBlank()) {
            return;
        }
        ResumeParseTask task = findTask(resumeId, taskNo);
        if (task == null) {
            return;
        }
        taskMapper.update(null, Wrappers.<ResumeParseTask>lambdaUpdate()
                .eq(ResumeParseTask::getId, task.getId())
                .set(ResumeParseTask::getStatus, status)
                .set(ResumeParseTask::getStage, stage)
                .set(ResumeParseTask::getProgress, progress)
                .set(ResumeParseTask::getErrorMsg, errorMsg));
    }

    private ResumeParseTask findTask(long resumeId, String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            return null;
        }
        return taskMapper.selectOne(Wrappers.<ResumeParseTask>lambdaQuery()
                .eq(ResumeParseTask::getResumeId, resumeId)
                .eq(ResumeParseTask::getTaskNo, taskNo)
                .last("LIMIT 1"));
    }

    private EventEnvelope<JsonNode> parse(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<EventEnvelope<JsonNode>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("无法解析 resume.parse.completed：{}", e.getOriginalMessage());
            return null;
        }
    }

    private long longValue(JsonNode payload, String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        return node == null || node.isNull() ? 0 : node.asLong(0);
    }

    private Long longOrNull(JsonNode payload, String field) {
        long value = longValue(payload, field);
        return value <= 0 ? null : value;
    }

    private String textOrNull(JsonNode payload, String field) {
        JsonNode node = payload == null ? null : payload.get(field);
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
