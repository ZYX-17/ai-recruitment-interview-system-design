package com.recruit.notify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.notify.entity.IdempotentConsumption;
import com.recruit.notify.entity.Notification;
import com.recruit.notify.entity.NotificationSendLog;
import com.recruit.notify.entity.NotificationTemplate;
import com.recruit.notify.mapper.IdempotentConsumptionMapper;
import com.recruit.notify.mapper.NotificationMapper;
import com.recruit.notify.mapper.NotificationSendLogMapper;
import com.recruit.notify.mapper.NotificationTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知消费与投递单测。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationTemplateMapper templateMapper;

    @Mock
    private NotificationSendLogMapper sendLogMapper;

    @Mock
    private IdempotentConsumptionMapper idempotentMapper;

    @Mock
    private MailSenderService mailSenderService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationMapper, templateMapper, sendLogMapper,
                idempotentMapper, new TemplateRenderer(new ObjectMapper()), mailSenderService);
        lenient().when(notificationMapper.insert(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(9001L);
            return 1;
        });
    }

    @Test
    void inappRequestCreatesSentNotificationAndLog() {
        when(idempotentMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(templateMapper.selectActiveLatest("interview_invite", "INAPP")).thenReturn(template());
        when(notificationMapper.selectByMsgNo(anyString())).thenReturn(null);
        when(notificationMapper.markSent(9001L)).thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        service.handleEvent(NotificationService.CONSUMER_GROUP, "evt-1", payload(List.of("INAPP")));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("QUEUED");
        assertThat(saved.getReceiverId()).isEqualTo(60001L);
        assertThat(saved.getTitle()).isEqualTo("【面试】Java");
        assertThat(saved.getContent()).isEqualTo("张三 您好");
        verify(notificationMapper).markSent(9001L);
        ArgumentCaptor<NotificationSendLog> logCaptor = ArgumentCaptor.forClass(NotificationSendLog.class);
        verify(sendLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getResult()).isEqualTo("OK");
        assertThat(logCaptor.getValue().getProvider()).isEqualTo("INAPP");
    }

    @Test
    void emailSuccessMarksSent() {
        when(idempotentMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(templateMapper.selectActiveLatest("interview_invite", "EMAIL")).thenReturn(template());
        when(notificationMapper.selectByMsgNo(anyString())).thenReturn(null);
        when(mailSenderService.send(anyString(), anyString(), anyString()))
                .thenReturn(DeliveryOutcome.ok("SMTP", "rid", System.nanoTime()));
        when(notificationMapper.markSent(9001L)).thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        service.handleEvent(NotificationService.CONSUMER_GROUP, "evt-2", payload(List.of("EMAIL")));

        verify(mailSenderService).send(eq("candidate@example.com"), eq("【面试】Java"), eq("张三 您好"));
        verify(notificationMapper).markSent(9001L);
        verify(notificationMapper, never()).markFailed(anyLong(), anyString(), anyInt(), any());
    }

    @Test
    void emailFailureSchedulesRetry() {
        when(idempotentMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(templateMapper.selectActiveLatest("interview_invite", "EMAIL")).thenReturn(template());
        when(notificationMapper.selectByMsgNo(anyString())).thenReturn(null);
        when(mailSenderService.send(anyString(), anyString(), anyString()))
                .thenReturn(DeliveryOutcome.fail("SMTP", "connection refused", System.nanoTime()));
        when(notificationMapper.markFailed(eq(9001L), eq("connection refused"), eq(1), any(LocalDateTime.class)))
                .thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        service.handleEvent(NotificationService.CONSUMER_GROUP, "evt-3", payload(List.of("EMAIL")));

        verify(notificationMapper).markFailed(eq(9001L), eq("connection refused"), eq(1), any(LocalDateTime.class));
        verify(notificationMapper, never()).markSent(9001L);
    }

    @Test
    void duplicateEventIsIgnoredBeforeTouchingTemplates() {
        when(idempotentMapper.insert(any(IdempotentConsumption.class)))
                .thenThrow(new DuplicateKeyException("uk_group_event"));

        service.handleEvent(NotificationService.CONSUMER_GROUP, "evt-4", payload(List.of("INAPP")));

        verify(templateMapper, never()).selectActiveLatest(anyString(), anyString());
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void businessTupleDedupSkipsNotification() {
        when(idempotentMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(templateMapper.selectActiveLatest("interview_invite", "INAPP")).thenReturn(template());
        when(notificationMapper.countRecentByBiz("INTERVIEW_INVITE", "90001", 60001L, "INAPP")).thenReturn(1L);

        NotificationRequestedPayload payload = new NotificationRequestedPayload("interview_invite",
                "60001", null, List.of("INAPP"), "INTERVIEW_INVITE", "90001", variables(), null);
        service.handleEvent(NotificationService.CONSUMER_GROUP, "evt-5", payload);

        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void missingTemplateFailsWithNtf6001() {
        when(idempotentMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(templateMapper.selectActiveLatest("interview_invite", "INAPP")).thenReturn(null);

        assertThatThrownBy(() -> service.handleEvent(NotificationService.CONSUMER_GROUP, "evt-6",
                payload(List.of("INAPP"))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("NTF6001");
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    void retrySuccessMarksSent() {
        Notification failed = failedEmail(2);
        when(notificationMapper.selectRetryCandidates(50)).thenReturn(List.of(failed));
        when(mailSenderService.send(anyString(), anyString(), anyString()))
                .thenReturn(DeliveryOutcome.ok("SMTP", "rid", System.nanoTime()));
        when(notificationMapper.markSent(9002L)).thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        service.retryFailed();

        verify(notificationMapper).markSent(9002L);
    }

    @Test
    void retryFailureIncrementsRetryCount() {
        Notification failed = failedEmail(1);
        when(notificationMapper.selectRetryCandidates(50)).thenReturn(List.of(failed));
        when(mailSenderService.send(anyString(), anyString(), anyString()))
                .thenReturn(DeliveryOutcome.fail("SMTP", "timeout", System.nanoTime()));
        when(notificationMapper.markFailed(eq(9002L), eq("timeout"), eq(2), any(LocalDateTime.class)))
                .thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        service.retryFailed();

        verify(notificationMapper).markFailed(eq(9002L), eq("timeout"), eq(2), any(LocalDateTime.class));
    }

    @Test
    void resendNonFailedNotificationIsIdempotent() {
        Notification sent = notification(9003L, "SENT", 0);
        when(notificationMapper.selectById(9003L)).thenReturn(sent);

        assertThat(service.resend(9003L).status()).isEqualTo("SENT");
        verify(notificationMapper, never()).resetForResend(anyLong(), anyInt());
    }

    @Test
    void resendFailedNotificationSendsAgain() {
        Notification failed = failedEmail(0);
        Notification after = notification(9002L, "SENT", 1);
        when(notificationMapper.selectById(9002L)).thenReturn(failed, after);
        when(notificationMapper.resetForResend(9002L, 0)).thenReturn(1);
        when(mailSenderService.send(anyString(), anyString(), anyString()))
                .thenReturn(DeliveryOutcome.ok("SMTP", "rid", System.nanoTime()));
        when(notificationMapper.markSent(9002L)).thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        assertThat(service.resend(9002L).status()).isEqualTo("SENT");
        verify(notificationMapper).resetForResend(9002L, 0);
        verify(notificationMapper).markSent(9002L);
    }

    @Test
    void resendFailureReturnsNtf6002() {
        Notification failed = failedEmail(0);
        Notification stillFailed = failedEmail(1);
        when(notificationMapper.selectById(9002L)).thenReturn(failed, stillFailed);
        when(notificationMapper.resetForResend(9002L, 0)).thenReturn(1);
        when(mailSenderService.send(anyString(), anyString(), anyString()))
                .thenReturn(DeliveryOutcome.fail("SMTP", "smtp down", System.nanoTime()));
        when(notificationMapper.markFailed(eq(9002L), eq("smtp down"), eq(1), any(LocalDateTime.class)))
                .thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        assertThatThrownBy(() -> service.resend(9002L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("NTF6002");
    }

    @Test
    void notificationProcessingCompletesWithinSixtySeconds() {
        when(idempotentMapper.insert(any(IdempotentConsumption.class))).thenReturn(1);
        when(templateMapper.selectActiveLatest("interview_invite", "INAPP")).thenReturn(template());
        when(notificationMapper.selectByMsgNo(anyString())).thenReturn(null);
        when(notificationMapper.markSent(9001L)).thenReturn(1);
        when(sendLogMapper.insert(any(NotificationSendLog.class))).thenReturn(1);

        long start = System.nanoTime();
        service.handleEvent(NotificationService.CONSUMER_GROUP, "evt-latency", payload(List.of("INAPP")));

        assertThat((System.nanoTime() - start) / 1_000_000L).isLessThan(60_000L);
    }

    private static NotificationRequestedPayload payload(List<String> channels) {
        return new NotificationRequestedPayload("interview_invite", "60001", "candidate@example.com",
                channels, "INTERVIEW_INVITE", "90001", variables(), "interview_invite:90001:60001");
    }

    private static Map<String, Object> variables() {
        return Map.of("candidateName", "张三", "jobTitle", "Java");
    }

    private static NotificationTemplate template() {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(1L);
        template.setTemplateCode("interview_invite");
        template.setChannel("EMAIL");
        template.setVersion(1);
        template.setTitleTpl("【面试】{{jobTitle}}");
        template.setContentTpl("{{candidateName}} 您好");
        template.setVariablesJson("[\"candidateName\",\"jobTitle\"]");
        template.setStatus("ACTIVE");
        return template;
    }

    private static Notification failedEmail(int retryCount) {
        Notification notification = notification(9002L, "FAILED", 0);
        notification.setRetryCount(retryCount);
        notification.setChannel("EMAIL");
        notification.setReceiverEmail("candidate@example.com");
        notification.setTitle("【面试】Java");
        notification.setContent("张三 您好");
        notification.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        return notification;
    }

    private static Notification notification(long id, String status, int version) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setMsgNo("MSG-" + id);
        notification.setReceiverId(60001L);
        notification.setReceiverEmail("candidate@example.com");
        notification.setChannel("EMAIL");
        notification.setBizType("INTERVIEW_INVITE");
        notification.setBizId("90001");
        notification.setTitle("【面试】Java");
        notification.setContent("张三 您好");
        notification.setStatus(status);
        notification.setRetryCount(0);
        notification.setVersion(version);
        return notification;
    }
}
