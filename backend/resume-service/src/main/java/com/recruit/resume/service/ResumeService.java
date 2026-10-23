package com.recruit.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.id.IdWorker;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.contracts.event.EventTypes;
import com.recruit.resume.client.FileClient;
import com.recruit.resume.client.dto.DownloadUrlDTO;
import com.recruit.resume.client.dto.FileMetaDTO;
import com.recruit.resume.client.dto.InternalPresignDownloadRequest;
import com.recruit.resume.dto.CreateResumeRequest;
import com.recruit.resume.dto.CreateResumeResponse;
import com.recruit.resume.dto.ParseCompletedRequest;
import com.recruit.resume.dto.ParseFailedRequest;
import com.recruit.resume.dto.ParseTaskVO;
import com.recruit.resume.dto.ResumeMarkdownVO;
import com.recruit.resume.dto.ResumeParseStatusVO;
import com.recruit.resume.dto.ResumeVO;
import com.recruit.resume.dto.UpdateStructuredRequest;
import com.recruit.resume.entity.Person;
import com.recruit.resume.entity.Resume;
import com.recruit.resume.entity.ResumeParseTask;
import com.recruit.resume.entity.ResumeVersion;
import com.recruit.resume.mapper.PersonMapper;
import com.recruit.resume.mapper.ResumeMapper;
import com.recruit.resume.mapper.ResumeParseTaskMapper;
import com.recruit.resume.mapper.ResumeVersionMapper;
import com.recruit.resume.support.ParseTaskStatus;
import com.recruit.resume.support.PhoneHasher;
import com.recruit.resume.support.ResumeStatus;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 简历核心服务：创建/查询/解析进度/人工编辑/重试。
 *
 * <p>创建链路（对应 docs/b/02-architecture.md §2.1）：
 * 校验 file-service 元数据 → 按 SHA-256 秒传判重 → 建 person/resume/parse_task
 * → 同事务写 Outbox {@code resume.received}。解析本身由 mineru-worker 异步完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_RETRY = 3;

    private final PersonMapper personMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeParseTaskMapper taskMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final FileClient fileClient;
    private final OutboxService outboxService;
    private final IdWorker idWorker;
    private final PhoneHasher phoneHasher;
    private final ObjectMapper objectMapper;

    /**
     * 创建简历。同一 SHA-256 已存在时直接返回已有记录（duplicated=true），不重复建任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateResumeResponse create(CreateResumeRequest request) {
        FileMetaDTO file = requireFile(request.fileId());
        if (!"OK".equalsIgnoreCase(file.status())) {
            throw new BizException(ErrorCode.FILE_UPLOAD_INCOMPLETE, "文件尚未完成上传");
        }
        if (!"RESUME".equalsIgnoreCase(file.bizType())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件业务类型必须为 RESUME");
        }
        if (StringUtils.hasText(request.fileKey())
                && !request.fileKey().trim().equals(file.fileKey())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "fileKey 与文件元数据不一致");
        }
        if (!StringUtils.hasText(file.sha256())) {
            throw new BizException(ErrorCode.FILE_UPLOAD_INCOMPLETE, "简历文件缺少 SHA-256，请重新上传");
        }

        Resume existing = findBySha256(file.sha256());
        if (existing != null) {
            ResumeParseTask task = latestTask(existing.getId());
            return new CreateResumeResponse(
                    String.valueOf(existing.getId()),
                    existing.getResumeNo(),
                    String.valueOf(existing.getPersonId()),
                    task == null ? null : task.getTaskNo(),
                    existing.getParseStatus(),
                    true);
        }

        Person person = resolvePerson(request);

        long resumeId = idWorker.nextId();
        Resume resume = new Resume();
        resume.setId(resumeId);
        resume.setResumeNo("R" + resumeId);
        resume.setPersonId(person.getId());
        resume.setFileId(request.fileId());
        resume.setFileKey(file.fileKey());
        resume.setFileName(file.fileName());
        resume.setSha256(file.sha256().toLowerCase(Locale.ROOT));
        resume.setChannel(normalizeChannel(request.channel()));
        resume.setParseStatus(ResumeStatus.PENDING);
        resume.setCurrentVersionNo(0);
        resumeMapper.insert(resume);

        ResumeParseTask task = new ResumeParseTask();
        long taskId = idWorker.nextId();
        task.setId(taskId);
        task.setTaskNo("PT" + taskId);
        task.setResumeId(resumeId);
        task.setStatus(ParseTaskStatus.PENDING);
        task.setStage("PARSE");
        task.setProgress(0);
        task.setRetryCount(0);
        taskMapper.insert(task);

        outboxService.append(EventTypes.RESUME_RECEIVED, String.valueOf(resumeId), "RESUME",
                receivedPayload(resume, task));

        return new CreateResumeResponse(String.valueOf(resumeId), resume.getResumeNo(),
                String.valueOf(person.getId()), task.getTaskNo(), resume.getParseStatus(), false);
    }

    public ResumeVO getResume(long resumeId) {
        Resume resume = requireResume(resumeId);
        return ResumeVO.from(resume, personMapper.selectById(resume.getPersonId()));
    }

    /**
     * 简历分页列表；keyword 匹配简历编号/文件名。
     */
    public PageResult<ResumeVO> listResumes(long pageNum, long pageSize, String keyword,
                                            String parseStatus, Long personId) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<Resume> query = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(w -> w.like(Resume::getResumeNo, value).or().like(Resume::getFileName, value));
        }
        if (StringUtils.hasText(parseStatus)) {
            query.eq(Resume::getParseStatus, parseStatus.trim().toUpperCase(Locale.ROOT));
        }
        if (personId != null) {
            query.eq(Resume::getPersonId, personId);
        }
        query.orderByDesc(Resume::getId);

        Page<Resume> page = resumeMapper.selectPage(new Page<>(safePageNum, safePageSize), query);
        List<ResumeVO> list = page.getRecords().stream()
                .map(resume -> ResumeVO.from(resume, personMapper.selectById(resume.getPersonId())))
                .toList();
        return PageResult.of(list, page.getTotal(), safePageNum, safePageSize);
    }

    public ResumeParseStatusVO getParseStatus(long resumeId) {
        Resume resume = requireResume(resumeId);
        return statusView(resume, latestTask(resumeId));
    }

    /**
     * mineru-worker 解析成功回调：任务成功、写 Markdown 文件、追加版本并发布
     * {@code resume.parse.completed}。重复回调按 taskNo 幂等返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseStatusVO markParseCompleted(long resumeId, ParseCompletedRequest request) {
        Resume resume = requireResume(resumeId);
        ResumeParseTask task = requireTask(resumeId, request.taskNo());
        if (ParseTaskStatus.SUCCESS.equals(task.getStatus())) {
            return statusView(resume, task);
        }

        int nextVersion = (resume.getCurrentVersionNo() == null ? 0 : resume.getCurrentVersionNo()) + 1;
        task.setStatus(ParseTaskStatus.SUCCESS);
        task.setStage("PARSE");
        task.setProgress(100);
        task.setErrorMsg(null);
        if (taskMapper.updateById(task) == 0) {
            throw new BizException(ErrorCode.SYS_ERROR, "任务状态已变更，请重试");
        }

        resume.setMarkdownFileId(request.markdownFileId());
        // 解析完成 ≠ 索引完成：STRUCTURING 阶段由 resume.parse.completed 消费者继续推进
        resume.setParseStatus(ResumeStatus.STRUCTURING);
        resume.setCurrentVersionNo(nextVersion);
        if (resumeMapper.updateById(resume) == 0) {
            throw new BizException(ErrorCode.RESUME_VERSION_CONFLICT);
        }

        ResumeVersion version = new ResumeVersion();
        version.setId(idWorker.nextId());
        version.setResumeId(resumeId);
        version.setVersionNo(nextVersion);
        version.setParserVersion(request.parserVersion());
        // 结构化实体抽取在 P5 完成；先落空对象保证版本链完整
        version.setStructuredJson("{}");
        version.setMarkdownFileId(request.markdownFileId());
        resumeVersionMapper.insert(version);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resumeId", String.valueOf(resumeId));
        payload.put("personId", String.valueOf(resume.getPersonId()));
        payload.put("taskNo", task.getTaskNo());
        payload.put("versionNo", nextVersion);
        payload.put("chunkCount", request.chunkCount() == null ? 0 : request.chunkCount());
        payload.put("markdownFileId", String.valueOf(request.markdownFileId()));
        payload.put("parserVersion", request.parserVersion());
        outboxService.append(EventTypes.RESUME_PARSE_COMPLETED, String.valueOf(resumeId), "RESUME", payload);

        return statusView(resume, task);
    }

    /**
     * mineru-worker 解析失败回调：更新失败/DEAD 状态并发布 {@code resume.parse.failed}。
     * 迟到的失败回调不能覆盖已经成功的任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeParseStatusVO markParseFailed(long resumeId, ParseFailedRequest request) {
        Resume resume = requireResume(resumeId);
        ResumeParseTask task = requireTask(resumeId, request.taskNo());
        if (ParseTaskStatus.SUCCESS.equals(task.getStatus())) {
            return statusView(resume, task);
        }

        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        boolean dead = retryCount >= MAX_RETRY;
        task.setStatus(dead ? ParseTaskStatus.DEAD : ParseTaskStatus.FAILED);
        task.setErrorMsg(truncate(request.reason(), 512));
        if (taskMapper.updateById(task) == 0) {
            throw new BizException(ErrorCode.SYS_ERROR, "任务状态已变更，请重试");
        }

        resume.setParseStatus(ResumeStatus.FAILED);
        if (resumeMapper.updateById(resume) == 0) {
            throw new BizException(ErrorCode.RESUME_VERSION_CONFLICT);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resumeId", String.valueOf(resumeId));
        payload.put("personId", String.valueOf(resume.getPersonId()));
        payload.put("taskNo", task.getTaskNo());
        payload.put("reason", truncate(request.reason(), 512));
        payload.put("attempt", retryCount + 1);
        outboxService.append(EventTypes.RESUME_PARSE_FAILED, String.valueOf(resumeId), "RESUME", payload);

        return statusView(resume, task);
    }

    /**
     * Markdown 产物下载地址；未生成时返回 RSM2101。
     */
    public ResumeMarkdownVO getMarkdownUrl(long resumeId) {
        Resume resume = requireResume(resumeId);
        if (resume.getMarkdownFileId() == null) {
            throw new BizException(ErrorCode.RESUME_PARSE_FAILED, "Markdown 尚未生成");
        }
        try {
            var result = fileClient.presignDownload(
                    new InternalPresignDownloadRequest(resume.getMarkdownFileId()));
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new BizException(ErrorCode.FILE_STORAGE_ERROR);
            }
            DownloadUrlDTO data = result.getData();
            return new ResumeMarkdownVO(data.url(), data.expireAt());
        } catch (FeignException.NotFound e) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        } catch (FeignException e) {
            log.error("获取 Markdown 下载地址失败 resumeId={}", resumeId, e);
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    /**
     * 人工编辑结构化结果：写入新版本，不回写旧版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResumeVO updateStructured(long resumeId, UpdateStructuredRequest request) {
        Resume resume = requireResume(resumeId);
        validateStructuredJson(request.structuredJson());
        if (!Objects.equals(resume.getVersion(), request.version())) {
            throw new BizException(ErrorCode.RESUME_VERSION_CONFLICT);
        }

        int nextVersion = (resume.getCurrentVersionNo() == null ? 0 : resume.getCurrentVersionNo()) + 1;
        resume.setStructuredJson(request.structuredJson());
        resume.setCurrentVersionNo(nextVersion);
        int updated = resumeMapper.updateById(resume);
        if (updated == 0) {
            throw new BizException(ErrorCode.RESUME_VERSION_CONFLICT);
        }

        ResumeVersion version = new ResumeVersion();
        version.setId(idWorker.nextId());
        version.setResumeId(resumeId);
        version.setVersionNo(nextVersion);
        version.setParserVersion("manual");
        version.setStructuredJson(request.structuredJson());
        version.setMarkdownFileId(resume.getMarkdownFileId());
        resumeVersionMapper.insert(version);

        return ResumeVO.from(resume, personMapper.selectById(resume.getPersonId()));
    }

    /**
     * 重试解析：retry_count 上限 3；已在 PENDING/RUNNING 时幂等返回当前任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public ParseTaskVO retryParse(long resumeId) {
        Resume resume = requireResume(resumeId);
        ResumeParseTask task = latestTask(resumeId);
        if (task == null) {
            throw new BizException(ErrorCode.PARSE_TASK_NOT_FOUND);
        }
        if (ParseTaskStatus.PENDING.equals(task.getStatus()) || ParseTaskStatus.RUNNING.equals(task.getStatus())) {
            return ParseTaskVO.from(task);
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= MAX_RETRY) {
            throw new BizException(ErrorCode.PARSE_RETRY_EXCEEDED);
        }

        task.setRetryCount(retryCount + 1);
        task.setStatus(ParseTaskStatus.PENDING);
        task.setStage("PARSE");
        task.setProgress(0);
        task.setErrorMsg(null);
        if (taskMapper.updateById(task) == 0) {
            throw new BizException(ErrorCode.SYS_ERROR, "任务状态已变更，请重试");
        }

        resume.setParseStatus(ResumeStatus.PENDING);
        if (resumeMapper.updateById(resume) == 0) {
            throw new BizException(ErrorCode.RESUME_VERSION_CONFLICT);
        }

        outboxService.append(EventTypes.RESUME_RECEIVED, String.valueOf(resumeId), "RESUME",
                receivedPayload(resume, task));
        return ParseTaskVO.from(task);
    }

    public PageResult<ParseTaskVO> listTasks(long pageNum, long pageSize, String status) {
        long safePageNum = Math.max(pageNum, 1);
        long safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<ResumeParseTask> query = Wrappers.lambdaQuery();
        if (StringUtils.hasText(status)) {
            query.eq(ResumeParseTask::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        query.orderByDesc(ResumeParseTask::getId);
        Page<ResumeParseTask> page = taskMapper.selectPage(new Page<>(safePageNum, safePageSize), query);
        List<ParseTaskVO> list = page.getRecords().stream().map(ParseTaskVO::from).toList();
        return PageResult.of(list, page.getTotal(), safePageNum, safePageSize);
    }

    public ParseTaskVO getTask(long id) {
        ResumeParseTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(ErrorCode.PARSE_TASK_NOT_FOUND);
        }
        return ParseTaskVO.from(task);
    }

    private FileMetaDTO requireFile(long fileId) {
        try {
            var result = fileClient.meta(fileId);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new BizException(ErrorCode.FILE_NOT_FOUND);
            }
            return result.getData();
        } catch (FeignException.NotFound e) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        } catch (FeignException e) {
            log.error("调用 file-service 失败 fileId={}", fileId, e);
            throw new BizException(ErrorCode.FILE_STORAGE_ERROR, e);
        }
    }

    private Person resolvePerson(CreateResumeRequest request) {
        if (request.personId() != null) {
            Person person = personMapper.selectById(request.personId());
            if (person == null) {
                throw new BizException(ErrorCode.PERSON_NOT_FOUND);
            }
            return person;
        }
        if (!StringUtils.hasText(request.name())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新建候选人时 name 不能为空");
        }
        Person person = new Person();
        person.setId(idWorker.nextId());
        person.setName(request.name().trim());
        person.setPhoneHash(phoneHasher.hash(request.phone()));
        person.setEmail(trimToNull(request.email()));
        person.setCity(trimToNull(request.city()));
        person.setEducation(trimToNull(request.education()));
        person.setWorkYears(request.workYears());
        person.setStatus("ACTIVE");
        personMapper.insert(person);
        return person;
    }

    private Resume requireResume(long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BizException(ErrorCode.RESUME_NOT_FOUND);
        }
        return resume;
    }

    private Resume findBySha256(String sha256) {
        return resumeMapper.selectOne(Wrappers.<Resume>lambdaQuery()
                .eq(Resume::getSha256, sha256.toLowerCase(Locale.ROOT))
                .last("LIMIT 1"));
    }

    private ResumeParseTask latestTask(long resumeId) {
        return taskMapper.selectOne(Wrappers.<ResumeParseTask>lambdaQuery()
                .eq(ResumeParseTask::getResumeId, resumeId)
                .orderByDesc(ResumeParseTask::getId)
                .last("LIMIT 1"));
    }

    private ResumeParseTask requireTask(long resumeId, String taskNo) {
        ResumeParseTask task = taskMapper.selectOne(Wrappers.<ResumeParseTask>lambdaQuery()
                .eq(ResumeParseTask::getResumeId, resumeId)
                .eq(ResumeParseTask::getTaskNo, taskNo)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BizException(ErrorCode.PARSE_TASK_NOT_FOUND);
        }
        return task;
    }

    private ResumeParseStatusVO statusView(Resume resume, ResumeParseTask task) {
        return new ResumeParseStatusVO(
                String.valueOf(resume.getId()),
                task == null ? null : task.getTaskNo(),
                resume.getParseStatus(),
                task == null ? null : task.getStage(),
                task == null ? null : task.getProgress(),
                task == null ? null : task.getErrorMsg(),
                task == null ? null : task.getRetryCount());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void validateStructuredJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "structuredJson 必须是 JSON 对象");
            }
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "structuredJson 不是合法 JSON");
        }
    }

    private Map<String, Object> receivedPayload(Resume resume, ResumeParseTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resumeId", String.valueOf(resume.getId()));
        payload.put("personId", String.valueOf(resume.getPersonId()));
        payload.put("taskNo", task.getTaskNo());
        payload.put("fileId", String.valueOf(resume.getFileId()));
        payload.put("fileKey", resume.getFileKey());
        payload.put("channel", resume.getChannel());
        return payload;
    }

    private String normalizeChannel(String channel) {
        String value = StringUtils.hasText(channel) ? channel.trim().toUpperCase(Locale.ROOT) : "PORTAL";
        return switch (value) {
            case "PORTAL", "EMAIL", "HR_IMPORT" -> value;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "不支持的 channel：" + channel);
        };
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
