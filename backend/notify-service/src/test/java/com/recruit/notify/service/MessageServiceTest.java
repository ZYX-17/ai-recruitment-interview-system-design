package com.recruit.notify.service;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.notify.dto.MessagePageQuery;
import com.recruit.notify.entity.Notification;
import com.recruit.notify.mapper.NotificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内信查询/已读单测。
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    private MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(notificationMapper);
        UserContext.set("60001", List.of("CANDIDATE"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void pageQueriesOnlyCurrentUser() {
        MessagePageQuery query = new MessagePageQuery();
        when(notificationMapper.countMessagePage(60001L, null, null, null)).thenReturn(1L);
        when(notificationMapper.selectMessagePage(60001L, null, null, null, 20, 0L))
                .thenReturn(List.of(notification(9001L, "SENT", 60001L)));
        when(notificationMapper.countUnread(60001L)).thenReturn(1L);

        var page = service.page(query);
        assertThat(page.list()).hasSize(1);
        assertThat(page.unreadCount()).isEqualTo(1L);
        verify(notificationMapper).countMessagePage(60001L, null, null, null);
    }

    @Test
    void markReadRejectsOtherUsersMessage() {
        when(notificationMapper.selectById(9001L)).thenReturn(notification(9001L, "SENT", 60002L));

        assertThatThrownBy(() -> service.markRead(9001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("AUTH1101");
        verify(notificationMapper, never()).markRead(9001L, 60001L);
    }

    @Test
    void markReadIsIdempotentForReadMessage() {
        when(notificationMapper.selectById(9001L)).thenReturn(notification(9001L, "READ", 60001L));

        service.markRead(9001L);

        verify(notificationMapper, never()).markRead(9001L, 60001L);
    }

    @Test
    void markReadUpdatesSentMessage() {
        when(notificationMapper.selectById(9001L)).thenReturn(notification(9001L, "SENT", 60001L));
        when(notificationMapper.markRead(9001L, 60001L)).thenReturn(1);

        service.markRead(9001L);

        verify(notificationMapper).markRead(9001L, 60001L);
    }

    @Test
    void markReadRejectsFailedMessage() {
        when(notificationMapper.selectById(9001L)).thenReturn(
                notification(9001L, "FAILED", 60001L), notification(9001L, "FAILED", 60001L));
        when(notificationMapper.markRead(9001L, 60001L)).thenReturn(0);

        assertThatThrownBy(() -> service.markRead(9001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("SYS400");
    }

    @Test
    void markReadMissingMessageReturns404() {
        when(notificationMapper.selectById(9001L)).thenReturn(null);

        assertThatThrownBy(() -> service.markRead(9001L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("SYS404");
    }

    private static Notification notification(long id, String status, long receiverId) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setMsgNo("MSG-" + id);
        notification.setReceiverId(receiverId);
        notification.setChannel("INAPP");
        notification.setBizType("INTERVIEW_INVITE");
        notification.setBizId("90001");
        notification.setTitle("面试邀约");
        notification.setContent("您好");
        notification.setStatus(status);
        notification.setReadAt("READ".equals(status) ? LocalDateTime.now() : null);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }
}
