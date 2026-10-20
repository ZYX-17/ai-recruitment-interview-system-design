package com.recruit.resume.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.id.IdWorker;
import com.recruit.contracts.event.EventTypes;
import com.recruit.resume.entity.LocalMessage;
import com.recruit.resume.mapper.LocalMessageMapper;
import com.recruit.resume.support.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private LocalMessageMapper localMessageMapper;

    @Mock
    private IdWorker idWorker;

    /** 与 Spring Boot 的 ObjectMapper 一致，注册 JavaTimeModule 以支持 Instant。 */
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(localMessageMapper, idWorker, objectMapper);
        ReflectionTestUtils.setField(outboxService, "exchange", "recruit.resume");
    }

    @Test
    void writesPendingEnvelopeWithPayload() throws Exception {
        when(idWorker.nextId()).thenReturn(1L);

        outboxService.append(EventTypes.RESUME_RECEIVED, "100", "RESUME",
                Map.of("resumeId", "100", "channel", "PORTAL"));

        ArgumentCaptor<LocalMessage> captor = ArgumentCaptor.forClass(LocalMessage.class);
        verify(localMessageMapper).insert(captor.capture());
        LocalMessage message = captor.getValue();

        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertEquals(EventTypes.RESUME_RECEIVED, message.getEventType());
        assertEquals(EventTypes.RESUME_RECEIVED, message.getRoutingKey());
        assertEquals("recruit.resume", message.getExchange());
        assertEquals(0, message.getRetryCount());
        assertNotNull(message.getMessageId());

        JsonNode envelope = objectMapper.readTree(message.getPayload());
        assertEquals(EventTypes.RESUME_RECEIVED, envelope.get("eventType").asText());
        assertEquals("100", envelope.get("aggregateId").asText());
        assertEquals("100", envelope.get("payload").get("resumeId").asText());
    }
}
