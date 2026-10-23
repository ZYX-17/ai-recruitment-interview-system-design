package com.recruit.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.id.IdWorker;
import com.recruit.contracts.event.EventEnvelope;
import com.recruit.contracts.event.EventTypes;
import com.recruit.resume.entity.JobSnapshot;
import com.recruit.resume.mapper.IdempotentConsumptionMapper;
import com.recruit.resume.mapper.JobSnapshotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 消费 interview-service 的 {@code job.published/job.updated}，维护 job_snapshot。
 *
 * <p>处理顺序：解析信封 → 幂等占位 → 版本比较 → upsert。幂等占位和 upsert 在同一事务，
 * upsert 失败会回滚幂等行，消息重投时可重新处理。</p>
 *
 * <p>payload 兼容 A 文档的 {@code version} 与 B 早期口径的 {@code jobVersion}；
 * job.updated 可能只带 changedFields，因此只更新事件里出现的字段。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSnapshotService {

    /** 消费组名；同一事件在同一组内只处理一次。 */
    public static final String CONSUMER_GROUP = "resume-service.job-events";

    private final JobSnapshotMapper jobSnapshotMapper;
    private final IdempotentConsumptionMapper idempotentConsumptionMapper;
    private final IdWorker idWorker;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void handle(String body) {
        EventEnvelope<JsonNode> envelope = parse(body);
        if (envelope == null) {
            return;
        }
        String eventType = envelope.getEventType();
        if (!EventTypes.JOB_PUBLISHED.equals(eventType) && !EventTypes.JOB_UPDATED.equals(eventType)) {
            log.debug("忽略非岗位事件 eventType={}", eventType);
            return;
        }
        if (!StringUtils.hasText(envelope.getEventId())) {
            log.warn("岗位事件缺少 eventId，忽略 eventType={}", eventType);
            return;
        }
        if (idempotentConsumptionMapper.insertIgnore(
                idWorker.nextId(), CONSUMER_GROUP, envelope.getEventId()) == 0) {
            log.debug("岗位事件重复，跳过 eventId={}", envelope.getEventId());
            return;
        }

        JsonNode payload = envelope.getPayload();
        if (payload == null || !payload.isObject()) {
            log.warn("岗位事件 payload 非法，忽略 eventId={}", envelope.getEventId());
            return;
        }
        long jobId = payload.path("jobId").asLong(0);
        if (jobId <= 0) {
            log.warn("岗位事件缺少 jobId，忽略 eventId={}", envelope.getEventId());
            return;
        }

        Integer incomingVersion = intOrNull(payload, "version", "jobVersion");
        JobSnapshot existing = jobSnapshotMapper.selectById(jobId);
        if (existing != null && incomingVersion != null && existing.getJobVersion() != null
                && incomingVersion < existing.getJobVersion()) {
            log.info("忽略过期岗位事件 jobId={}, incomingVersion={}, currentVersion={}",
                    jobId, incomingVersion, existing.getJobVersion());
            return;
        }

        if (existing == null) {
            JobSnapshot snapshot = new JobSnapshot();
            snapshot.setId(jobId);
            snapshot.setJobVersion(incomingVersion == null ? 0 : incomingVersion);
            snapshot.setTitle(textOr(payload, "未命名岗位", "title"));
            snapshot.setDeptName(textOrNull(payload, "deptName"));
            snapshot.setRequirementsJson(requirementsJson(payload));
            snapshot.setStatus(textOr(payload, "OPEN", "status"));
            snapshot.setSourceEventId(envelope.getEventId());
            jobSnapshotMapper.insert(snapshot);
            log.info("岗位快照已创建 jobId={}, title={}", jobId, snapshot.getTitle());
            return;
        }

        if (incomingVersion != null) {
            existing.setJobVersion(incomingVersion);
        }
        if (payload.hasNonNull("title")) {
            existing.setTitle(payload.get("title").asText());
        }
        if (payload.hasNonNull("deptName")) {
            existing.setDeptName(payload.get("deptName").asText());
        }
        String requirements = requirementsJson(payload);
        if (requirements != null) {
            existing.setRequirementsJson(requirements);
        }
        if (payload.hasNonNull("status")) {
            existing.setStatus(payload.get("status").asText());
        }
        existing.setSourceEventId(envelope.getEventId());
        jobSnapshotMapper.updateById(existing);
        log.info("岗位快照已更新 jobId={}, version={}, status={}",
                jobId, existing.getJobVersion(), existing.getStatus());
    }

    private EventEnvelope<JsonNode> parse(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<EventEnvelope<JsonNode>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("无法解析岗位事件，忽略：{}", e.getOriginalMessage());
            return null;
        }
    }

    private Integer intOrNull(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = payload.get(fieldName);
            if (node != null && !node.isNull()) {
                if (node.isNumber()) {
                    return node.asInt();
                }
                if (node.isTextual()) {
                    try {
                        return Integer.parseInt(node.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // 继续尝试下一个字段
                    }
                }
            }
        }
        return null;
    }

    /**
     * 兼容 requirements / requirementsJson / jdText 三种字段名；对象会被序列化成 JSON 字符串。
     */
    private String requirementsJson(JsonNode payload) {
        JsonNode node = firstPresent(payload, "requirements", "requirementsJson");
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            log.warn("岗位要求序列化失败，忽略该字段：{}", e.getOriginalMessage());
            return null;
        }
    }

    private String textOr(JsonNode payload, String fallback, String fieldName) {
        String value = textOrNull(payload, fieldName);
        return value == null ? fallback : value;
    }

    private String textOrNull(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText();
    }

    private JsonNode firstPresent(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = payload.get(fieldName);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        return null;
    }
}
