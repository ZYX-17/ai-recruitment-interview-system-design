package com.recruit.contracts.event;

import com.recruit.contracts.event.payload.InterviewScheduledPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件信封工厂测试。
 *
 * <p>{@link EventEnvelope#of} 的价值是把 eventVersion 和 occurredAt 收敛到一处生成。
 * 如果业务代码自己 builder，很容易漏填版本，消费方就无法做兼容判断，
 * 所以这里验证“工厂一定会填版本和时间”。</p>
 */
class EventEnvelopeTest {

    @Test
    @DisplayName("工厂自动填充版本、发生时间，并保留业务字段")
    void ofFillsVersionAndOccurredAt() {
        InterviewScheduledPayload payload = new InterviewScheduledPayload(
                "1001", "2002", 1, java.util.List.of("2001", "2002"),
                "3003", "张三",
                Instant.parse("2026-11-01T02:00:00Z"), Instant.parse("2026-11-01T03:00:00Z"),
                "ONLINE", "ONE_TO_ONE", null, "Java 后端工程师");

        Instant before = Instant.now();
        EventEnvelope<InterviewScheduledPayload> envelope = EventEnvelope.of(
                "evt-1", EventTypes.INTERVIEW_SCHEDULED, "3003", "interview", "trace-1", payload);
        Instant after = Instant.now();

        assertEquals("evt-1", envelope.getEventId());
        assertEquals(EventTypes.INTERVIEW_SCHEDULED, envelope.getEventType());
        assertEquals(EventSchemaVersion.V1, envelope.getEventVersion());
        assertEquals("3003", envelope.getAggregateId());
        assertEquals("interview", envelope.getAggregateType());
        assertEquals("trace-1", envelope.getTraceId());
        assertEquals(payload, envelope.getPayload());
        assertNotNull(envelope.getOccurredAt());
        assertTrue(!envelope.getOccurredAt().isBefore(before) && !envelope.getOccurredAt().isAfter(after),
                "occurredAt 应落在工厂调用期间");
    }

    @Test
    @DisplayName("未登记事件无法创建信封")
    void unknownEventCannotBuildEnvelope() {
        assertThrows(IllegalArgumentException.class, () -> EventEnvelope.of(
                "evt-2", "unknown.event", "1", "unknown", "trace-2", null));
    }
}
