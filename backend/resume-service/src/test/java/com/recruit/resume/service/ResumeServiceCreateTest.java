package com.recruit.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.id.IdWorker;
import com.recruit.common.result.Result;
import com.recruit.contracts.event.EventTypes;
import com.recruit.resume.client.FileClient;
import com.recruit.resume.client.dto.FileMetaDTO;
import com.recruit.resume.dto.CreateResumeRequest;
import com.recruit.resume.dto.CreateResumeResponse;
import com.recruit.resume.entity.Person;
import com.recruit.resume.entity.Resume;
import com.recruit.resume.entity.ResumeParseTask;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceCreateTest {

    private static final String SHA256 = "a".repeat(64);

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
    void createsPersonResumeTaskAndOutbox() {
        when(fileClient.meta(100L)).thenReturn(Result.ok(fileMeta()));
        when(resumeMapper.selectOne(any())).thenReturn(null);
        when(phoneHasher.hash("13800000000")).thenReturn("phone-hash");
        when(personMapper.insert(any(Person.class))).thenReturn(1);
        when(resumeMapper.insert(any(Resume.class))).thenReturn(1);
        when(taskMapper.insert(any(ResumeParseTask.class))).thenReturn(1);

        CreateResumeResponse response = resumeService.create(new CreateResumeRequest(
                100L, "key-1", null, null, "张三", "13800000000",
                "z@example.com", "上海", "本科", 3));

        assertFalse(response.duplicated());
        assertEquals("PENDING", response.parseStatus());
        assertNotNull(response.resumeId());
        assertNotNull(response.taskNo());

        ArgumentCaptor<Person> personCaptor = ArgumentCaptor.forClass(Person.class);
        verify(personMapper).insert(personCaptor.capture());
        assertEquals("张三", personCaptor.getValue().getName());
        assertEquals("phone-hash", personCaptor.getValue().getPhoneHash());
        assertEquals("ACTIVE", personCaptor.getValue().getStatus());

        ArgumentCaptor<Resume> resumeCaptor = ArgumentCaptor.forClass(Resume.class);
        verify(resumeMapper).insert(resumeCaptor.capture());
        Resume resume = resumeCaptor.getValue();
        assertEquals(SHA256, resume.getSha256());
        assertEquals("key-1", resume.getFileKey());
        assertEquals("PORTAL", resume.getChannel());
        assertEquals("PENDING", resume.getParseStatus());
        assertEquals(personCaptor.getValue().getId(), resume.getPersonId());

        ArgumentCaptor<ResumeParseTask> taskCaptor = ArgumentCaptor.forClass(ResumeParseTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals("PENDING", taskCaptor.getValue().getStatus());
        assertEquals("PARSE", taskCaptor.getValue().getStage());
        assertEquals(0, taskCaptor.getValue().getRetryCount());

        verify(outboxService).append(eq(EventTypes.RESUME_RECEIVED),
                eq(String.valueOf(resume.getId())), eq("RESUME"), any());
    }

    @Test
    void duplicateShaReturnsExistingWithoutInsert() {
        Resume existing = new Resume();
        existing.setId(9L);
        existing.setResumeNo("R9");
        existing.setPersonId(7L);
        existing.setParseStatus("SUCCESS");
        ResumeParseTask task = new ResumeParseTask();
        task.setTaskNo("PT9");

        when(fileClient.meta(100L)).thenReturn(Result.ok(fileMeta()));
        when(resumeMapper.selectOne(any())).thenReturn(existing);
        when(taskMapper.selectOne(any())).thenReturn(task);

        CreateResumeResponse response = resumeService.create(new CreateResumeRequest(
                100L, null, null, null, "张三", null, null, null, null, null));

        assertTrue(response.duplicated());
        assertEquals("R9", response.resumeNo());
        verify(resumeMapper, never()).insert(any(Resume.class));
        verify(personMapper, never()).insert(any(Person.class));
        verify(outboxService, never()).append(any(), any(), any(), any());
    }

    private FileMetaDTO fileMeta() {
        return new FileMetaDTO("100", "key-1", "张三-简历.pdf", "application/pdf", 123L,
                "RESUME", null, SHA256, "OK", null, null);
    }
}
