package com.recruit.resume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.id.IdWorker;
import com.recruit.resume.entity.JobSnapshot;
import com.recruit.resume.mapper.IdempotentConsumptionMapper;
import com.recruit.resume.mapper.JobSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSnapshotServiceTest {

    @Mock
    private JobSnapshotMapper jobSnapshotMapper;
    @Mock
    private IdempotentConsumptionMapper idempotentConsumptionMapper;

    private JobSnapshotService jobSnapshotService;

    @BeforeEach
    void setUp() {
        jobSnapshotService = new JobSnapshotService(jobSnapshotMapper, idempotentConsumptionMapper,
                new IdWorker(1, 1), new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createsSnapshotFromPublishedEvent() {
        when(idempotentConsumptionMapper.insertIgnore(anyLong(), anyString(), eq("e1"))).thenReturn(1);
        when(jobSnapshotMapper.selectById(100L)).thenReturn(null);

        jobSnapshotService.handle("""
                {
                  "eventId": "e1",
                  "eventType": "job.published",
                  "eventVersion": 1,
                  "aggregateId": "100",
                  "aggregateType": "JOB",
                  "occurredAt": "2026-10-20T00:00:00Z",
                  "payload": {
                    "jobId": 100,
                    "title": "Java 后端",
                    "deptName": "研发部",
                    "version": 3,
                    "status": "PUBLISHED",
                    "requirements": {"skills": ["Java", "Spring"]}
                  }
                }
                """);

        ArgumentCaptor<JobSnapshot> captor = ArgumentCaptor.forClass(JobSnapshot.class);
        verify(jobSnapshotMapper).insert(captor.capture());
        JobSnapshot snapshot = captor.getValue();
        assertEquals(100L, snapshot.getId());
        assertEquals(3, snapshot.getJobVersion());
        assertEquals("Java 后端", snapshot.getTitle());
        assertEquals("研发部", snapshot.getDeptName());
        assertEquals("PUBLISHED", snapshot.getStatus());
        assertEquals("e1", snapshot.getSourceEventId());
        assertTrue(snapshot.getRequirementsJson().contains("Java"));
    }

    @Test
    void duplicateEventIsSkipped() {
        when(idempotentConsumptionMapper.insertIgnore(anyLong(), anyString(), eq("e1"))).thenReturn(0);

        jobSnapshotService.handle("""
                {"eventId":"e1","eventType":"job.updated","occurredAt":"2026-10-20T00:00:00Z",
                 "payload":{"jobId":100,"version":1,"status":"CLOSED"}}
                """);

        verifyNoInteractions(jobSnapshotMapper);
    }

    @Test
    void staleVersionDoesNotOverwriteSnapshot() {
        JobSnapshot existing = new JobSnapshot();
        existing.setId(100L);
        existing.setJobVersion(5);
        when(idempotentConsumptionMapper.insertIgnore(anyLong(), anyString(), eq("e2"))).thenReturn(1);
        when(jobSnapshotMapper.selectById(100L)).thenReturn(existing);

        jobSnapshotService.handle("""
                {"eventId":"e2","eventType":"job.updated","occurredAt":"2026-10-20T00:00:00Z",
                 "payload":{"jobId":100,"version":3,"title":"旧标题"}}
                """);

        verify(jobSnapshotMapper, never()).updateById(org.mockito.ArgumentMatchers.any(JobSnapshot.class));
        assertEquals(5, existing.getJobVersion());
        assertEquals(null, existing.getTitle());
    }

    @Test
    void partialUpdateOnlyAppliesPresentFields() {
        JobSnapshot existing = new JobSnapshot();
        existing.setId(100L);
        existing.setJobVersion(2);
        existing.setTitle("原岗位");
        existing.setStatus("PUBLISHED");
        when(idempotentConsumptionMapper.insertIgnore(anyLong(), anyString(), eq("e3"))).thenReturn(1);
        when(jobSnapshotMapper.selectById(100L)).thenReturn(existing);

        jobSnapshotService.handle("""
                {"eventId":"e3","eventType":"job.updated","occurredAt":"2026-10-20T00:00:00Z",
                 "payload":{"jobId":100,"version":4,"status":"CLOSED"}}
                """);

        assertEquals(4, existing.getJobVersion());
        assertEquals("原岗位", existing.getTitle());
        assertEquals("CLOSED", existing.getStatus());
        verify(jobSnapshotMapper).updateById(existing);
    }
}
