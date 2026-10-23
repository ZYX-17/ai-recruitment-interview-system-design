package com.recruit.resume.service;

import com.recruit.resume.client.AiWorkerClient;
import com.recruit.resume.client.dto.EmbedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private AiWorkerClient aiWorkerClient;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService = new EmbeddingService(aiWorkerClient);
        ReflectionTestUtils.setField(embeddingService, "dims", 512);
        ReflectionTestUtils.setField(embeddingService, "batchSize", 32);
        ReflectionTestUtils.setField(embeddingService, "fallbackMock", true);
    }

    @Test
    void returnsVectorsFromAiWorker() {
        when(aiWorkerClient.embed(anyList())).thenReturn(new EmbedResponse(
                List.of(vector(), vector()), 512, "bge-small-zh-v1.5", "remote"));

        EmbeddingService.EmbeddingResult result = embeddingService.embed(List.of("a", "b"));

        assertEquals(2, result.vectors().size());
        assertEquals(512, result.vectors().get(0).length);
        assertEquals("bge-small-zh-v1.5", result.model());
        assertEquals("remote", result.mode());
    }

    @Test
    void fallsBackToLocalMockWhenAiWorkerFails() {
        when(aiWorkerClient.embed(anyList())).thenThrow(new RuntimeException("connection refused"));

        EmbeddingService.EmbeddingResult result = embeddingService.embed(List.of("Java", "Spring"));

        assertEquals(2, result.vectors().size());
        assertEquals(512, result.vectors().get(0).length);
        assertEquals("mock-local", result.model());
        assertEquals("mock", result.mode());
    }

    @Test
    void emptyInputReturnsEmptyResult() {
        EmbeddingService.EmbeddingResult result = embeddingService.embed(List.of());

        assertEquals(0, result.vectors().size());
        assertEquals("none", result.model());
    }

    private List<Float> vector() {
        return Collections.nCopies(512, 0.01f);
    }
}
