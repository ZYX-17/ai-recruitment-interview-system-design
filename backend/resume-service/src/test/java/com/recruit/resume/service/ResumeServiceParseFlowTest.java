package com.recruit.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.id.IdWorker;
import com.recruit.contracts.event.EventTypes;
import com.recruit.resume.client.FileClient;
import com.recruit.resume.dto.ParseCompletedRequest;
import com.recruit.resume.dto.ParseFailedRequest;
import com.recruit.resume.dto.ResumeParseStatusVO;
import com.recruit.resume.entity.Resume;
import com.recruit.resume.entity.ResumeParseTask;
import com.recruit.resume.entity.ResumeVersion;
import com.recruit.resume.mapper.PersonMapper;
import com.recruit.resume.mapper.ResumeMapper;
import com.recruit.resume.mapper.ResumeParseTaskMapper;
import com.recruit.resume.mapper.ResumeVersionMapper;
import com.recruit.resume.support.PhoneHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceParseFlowTest {

    @Mock
    private PersonMapper personMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeParseTaskMapper taskMapper;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private FileClient fileClient;
    @Mock
    private OutboxService outboxService;
    @Mock
    private PhoneHasher phoneHasher;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(personMapper, resumeMapper, taskMapper, resumeVersionMapper,
                fileClient, outboxService, new IdWorker(1, 1), phoneHasher, new ObjectMapper());
    }

    @Test
    void completedCallbackUpdatesTaskResumeAndVersion() {
        Resume resume = resume("PARSING", 0);
        ResumeParseTask task = task("RUNNING", 0);
        when(resumeMapper.selectById(1L)).thenReturn(resume);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(taskMapper.updateById(any(ResumeParseTask.class))).thenReturn(1);
        when(resumeMapper.updateById(any(Resume.class))).thenReturn(1);
        when(resumeVersionMapper.insert(any(ResumeVersion.class))).thenReturn(1);

        ResumeParseStatusVO status = resumeService.markParseCompleted(1L,
                new ParseCompletedRequest("PT1", 900L, "mock-1.0", 0));

        assertEquals("SUCCESS", status.parseStatus());
        assertEquals(100, status.progress());
        assertEquals("SUCCESS", resume.getParseStatus());
        assertEquals(900L, resume.getMarkdownFileId());
        assertEquals(1, resume.getCurrentVersionNo());

        ArgumentCaptor<ResumeVersion> versionCaptor = ArgumentCaptor.forClass(ResumeVersion.class);
        verify(resumeVersionMapper).insert(versionCaptor.capture());
        ResumeVersion version = versionCaptor.getValue();
        assertEquals(1, version.getVersionNo());
        assertEquals("mock-1.0", version.getParserVersion());
        assertEquals("{}", version.getStructuredJson());
        assertEquals(900L, version.getMarkdownFileId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService).append(eq(EventTypes.RESUME_PARSE_COMPLETED), eq("1"), eq("RESUME"),
                payloadCaptor.capture());
        assertEquals("1", payloadCaptor.getValue().get("resumeId"));
        assertEquals("PT1", payloadCaptor.getValue().get("taskNo"));
        assertEquals(1, payloadCaptor.getValue().get("versionNo"));
    }

    @Test
    void completedCallbackIsIdempotentWhenTaskAlreadySucceeded() {
        Resume resume = resume("SUCCESS", 1);
        ResumeParseTask task = task("SUCCESS", 0);
        when(resumeMapper.selectById(1L)).thenReturn(resume);
        when(taskMapper.selectOne(any())).thenReturn(task);

        ResumeParseStatusVO status = resumeService.markParseCompleted(1L,
                new ParseCompletedRequest("PT1", 900L, "mock-1.0", 0));

        assertEquals("SUCCESS", status.parseStatus());
        verify(taskMapper, never()).updateById(any(ResumeParseTask.class));
        verify(resumeMapper, never()).updateById(any(Resume.class));
        verify(outboxService, never()).append(anyString(), anyString(), anyString(), any());
    }

    @Test
    void failedCallbackMarksTaskFailedAndPublishesEvent() {
        Resume resume = resume("PARSING", 0);
        ResumeParseTask task = task("RUNNING", 1);
        when(resumeMapper.selectById(1L)).thenReturn(resume);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(taskMapper.updateById(any(ResumeParseTask.class))).thenReturn(1);
        when(resumeMapper.updateById(any(Resume.class))).thenReturn(1);

        ResumeParseStatusVO status = resumeService.markParseFailed(1L,
                new ParseFailedRequest("PT1", "MinerU 超时"));

        assertEquals("FAILED", status.parseStatus());
        assertEquals("FAILED", task.getStatus());
        assertEquals("MinerU 超时", task.getErrorMsg());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService).append(eq(EventTypes.RESUME_PARSE_FAILED), eq("1"), eq("RESUME"),
                payloadCaptor.capture());
        assertEquals(2, payloadCaptor.getValue().get("attempt"));
    }

    @Test
    void failedCallbackTurnsDeadAfterMaxRetries() {
        Resume resume = resume("PARSING", 0);
        ResumeParseTask task = task("RUNNING", 3);
        when(resumeMapper.selectById(1L)).thenReturn(resume);
        when(taskMapper.selectOne(any())).thenReturn(task);
        when(taskMapper.updateById(any(ResumeParseTask.class))).thenReturn(1);
        when(resumeMapper.updateById(any(Resume.class))).thenReturn(1);

        resumeService.markParseFailed(1L, new ParseFailedRequest("PT1", "still failing"));

        assertEquals("DEAD", task.getStatus());
    }

    private Resume resume(String parseStatus, int currentVersionNo) {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setResumeNo("R1");
        resume.setPersonId(7L);
        resume.setParseStatus(parseStatus);
        resume.setCurrentVersionNo(currentVersionNo);
        resume.setVersion(0);
        return resume;
    }

    private ResumeParseTask task(String status, int retryCount) {
        ResumeParseTask task = new ResumeParseTask();
        task.setId(2L);
        task.setTaskNo("PT1");
        task.setResumeId(1L);
        task.setStatus(status);
        task.setStage("PARSE");
        task.setProgress(0);
        task.setRetryCount(retryCount);
        task.setVersion(0);
        return task;
    }
}
