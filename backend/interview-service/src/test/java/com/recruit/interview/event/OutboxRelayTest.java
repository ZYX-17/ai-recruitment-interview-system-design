package com.recruit.interview.event;

import com.recruit.interview.entity.LocalMessage;
import com.recruit.interview.mapper.LocalMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Outbox 投递扫描（P11-3 前置）。
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private LocalMessageMapper messageMapper;
    @Mock
    private OutboxSender sender;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(messageMapper, sender, true, 100, 5);
    }

    @Test
    void ackMarksMessageSent() {
        LocalMessage message = message(1L, 0);
        when(messageMapper.selectPendingForRelay(any(), eq(100))).thenReturn(List.of(message));
        when(sender.send("recruit.events", "interview.scheduled", "{}")).thenReturn(null);

        int sent = relay.relayBatch();

        assertThat(sent).isEqualTo(1);
        verify(messageMapper).markSent(eq(1L), any(LocalDateTime.class));
        verify(messageMapper, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void failureSchedulesExponentialBackoff() {
        LocalMessage message = message(2L, 2);
        when(messageMapper.selectPendingForRelay(any(), eq(100))).thenReturn(List.of(message));
        when(sender.send(any(), any(), any())).thenReturn("broker nack");

        relay.relayBatch();

        verify(messageMapper).markRetry(eq(2L), eq(3), any(LocalDateTime.class),
                any(LocalDateTime.class));
        verify(messageMapper, never()).markDead(anyLong(), anyInt(), any());
    }

    @Test
    void failureBeyondMaxRetryMarksDead() {
        LocalMessage message = message(3L, 5);
        when(messageMapper.selectPendingForRelay(any(), eq(100))).thenReturn(List.of(message));
        when(sender.send(any(), any(), any())).thenReturn("still down");

        relay.relayBatch();

        verify(messageMapper).markDead(eq(3L), eq(6), any(LocalDateTime.class));
        verify(messageMapper, never()).markRetry(anyLong(), anyInt(), any(), any());
    }

    @Test
    void disabledRelayDoesNotScan() {
        OutboxRelay disabled = new OutboxRelay(messageMapper, sender, false, 100, 5);

        disabled.scan();

        verifyNoInteractions(messageMapper, sender);
    }

    @Test
    void batchCountsOnlyConfirmedMessages() {
        LocalMessage first = message(11L, 0);
        LocalMessage second = message(12L, 0);
        when(messageMapper.selectPendingForRelay(any(), eq(100))).thenReturn(List.of(first, second));
        when(sender.send(any(), any(), any())).thenReturn(null).thenReturn("nack");

        int sent = relay.relayBatch();

        assertThat(sent).isEqualTo(1);
        verify(messageMapper).markSent(eq(11L), any(LocalDateTime.class));
        verify(messageMapper).markRetry(eq(12L), eq(1), any(LocalDateTime.class),
                any(LocalDateTime.class));
    }

    private static LocalMessage message(long id, int retryCount) {
        LocalMessage message = new LocalMessage();
        message.setId(id);
        message.setMessageId("evt-" + id);
        message.setEventType("interview.scheduled");
        message.setExchange("recruit.events");
        message.setRoutingKey("interview.scheduled");
        message.setPayload("{}");
        message.setStatus("PENDING");
        message.setRetryCount(retryCount);
        return message;
    }
}
