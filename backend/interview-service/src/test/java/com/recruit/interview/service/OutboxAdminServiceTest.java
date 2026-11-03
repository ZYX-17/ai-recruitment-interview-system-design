package com.recruit.interview.service;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.OutboxMessageView;
import com.recruit.interview.dto.OutboxPageQuery;
import com.recruit.interview.entity.LocalMessage;
import com.recruit.interview.mapper.LocalMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Outbox 管理（P11-3，X-03/X-12）。
 */
@ExtendWith(MockitoExtension.class)
class OutboxAdminServiceTest {

    @Mock
    private LocalMessageMapper messageMapper;

    private OutboxAdminService service;

    @BeforeEach
    void setUp() {
        service = new OutboxAdminService(messageMapper);
    }

    @Test
    void pageNormalizesStatusAndMapsRows() {
        when(messageMapper.countOutboxPage(eq("DEAD"), isNull())).thenReturn(1L);
        when(messageMapper.selectOutboxPage(eq("DEAD"), isNull(), eq(20), eq(0L)))
                .thenReturn(List.of(message(11L, "DEAD", 3)));
        OutboxPageQuery query = new OutboxPageQuery();
        query.setStatus("dead");

        PageResult<OutboxMessageView> page = service.page(query);

        assertThat(page.getTotal()).isEqualTo(1);
        OutboxMessageView view = page.getList().get(0);
        assertThat(view.id()).isEqualTo("11");
        assertThat(view.status()).isEqualTo("DEAD");
        assertThat(view.retryCount()).isEqualTo(3);
        assertThat(view.messageId()).isEqualTo("evt-11");
    }

    @Test
    void pageSkipsQueryWhenEmpty() {
        when(messageMapper.countOutboxPage(isNull(), isNull())).thenReturn(0L);

        PageResult<OutboxMessageView> page = service.page(new OutboxPageQuery());

        assertThat(page.getList()).isEmpty();
        verify(messageMapper, never()).selectOutboxPage(any(), any(), anyInt(), anyLong());
    }

    @Test
    void pageRejectsUnknownStatus() {
        OutboxPageQuery query = new OutboxPageQuery();
        query.setStatus("UNKNOWN");

        assertThatThrownBy(() -> service.page(query))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void replayOnlyAllowsDeadOrFailed() {
        when(messageMapper.selectById(11L)).thenReturn(message(11L, "PENDING", 0));

        assertThatThrownBy(() -> service.replay(11L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ILLEGAL_TRANSITION));
        verify(messageMapper, never()).replay(anyLong(), any(LocalDateTime.class));
    }

    @Test
    void replayResetsDeadMessage() {
        LocalMessage dead = message(11L, "DEAD", 5);
        LocalMessage pending = message(11L, "PENDING", 0);
        when(messageMapper.selectById(11L)).thenReturn(dead).thenReturn(pending);

        OutboxMessageView view = service.replay(11L);

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(messageMapper).replay(eq(11L), now.capture());
        assertThat(now.getValue()).isNotNull();
        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.retryCount()).isZero();
    }

    @Test
    void replayRejectsMissingMessage() {
        when(messageMapper.selectById(11L)).thenReturn(null);

        assertThatThrownBy(() -> service.replay(11L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static LocalMessage message(long id, String status, int retryCount) {
        LocalMessage message = new LocalMessage();
        message.setId(id);
        message.setMessageId("evt-" + id);
        message.setEventType("interview.scheduled");
        message.setEventVersion(1);
        message.setAggregateId("90001");
        message.setStatus(status);
        message.setRetryCount(retryCount);
        message.setCreatedAt(LocalDateTime.of(2026, 10, 20, 1, 0));
        message.setUpdatedAt(LocalDateTime.of(2026, 10, 20, 1, 0));
        message.setVersion(0);
        return message;
    }
}
