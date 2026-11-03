package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.contracts.event.EventTypes;
import com.recruit.contracts.event.payload.NotificationRequestedPayload;
import com.recruit.contracts.event.payload.OfferConfirmedPayload;
import com.recruit.contracts.event.payload.OfferDeclinedPayload;
import com.recruit.contracts.event.payload.OfferSentPayload;
import com.recruit.interview.dto.ConfirmLinkView;
import com.recruit.interview.dto.OfferApproveForm;
import com.recruit.interview.dto.OfferCreateForm;
import com.recruit.interview.dto.OfferRespondForm;
import com.recruit.interview.dto.OfferSendForm;
import com.recruit.interview.dto.OfferView;
import com.recruit.interview.dto.PortalOfferView;
import com.recruit.interview.entity.Application;
import com.recruit.interview.entity.ApplicationStageHistory;
import com.recruit.interview.entity.Job;
import com.recruit.interview.entity.Offer;
import com.recruit.interview.event.OutboxPublisher;
import com.recruit.interview.mapper.ApplicationMapper;
import com.recruit.interview.mapper.ApplicationStageHistoryMapper;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.OfferMapper;
import com.recruit.interview.mapper.row.OfferRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Offer 创建/发送/审批/撤回/门户确认/过期（P7-1~P7-7）。
 */
@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock
    private OfferMapper offerMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationStageHistoryMapper historyMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerMapper, applicationMapper, historyMapper, jobMapper,
                outboxPublisher, new ObjectMapper(), dataScopeService, idempotencyService,
                redisTemplate);
        UserContext.set("10001", List.of("HR_ADMIN"), "HR", "trace");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createPersistsDraftWithHashedToken() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(offerMapper.selectRowByApplicationId(51001L)).thenReturn(null);
        when(offerMapper.insert(any(Offer.class))).thenAnswer(invocation -> {
            Offer offer = invocation.getArgument(0);
            offer.setId(92001L);
            return 1;
        });
        OfferRow saved = row(92001L, "DRAFT");
        saved.setOfferNo("OFFER-20261103-ABC123");
        when(offerMapper.selectRowById(92001L)).thenReturn(saved);

        OfferView view = service.create(createForm(), null);

        ArgumentCaptor<Offer> captor = ArgumentCaptor.forClass(Offer.class);
        verify(offerMapper).insert(captor.capture());
        Offer inserted = captor.getValue();
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getConfirmTokenHash()).hasSize(64);
        assertThat(inserted.getOfferNo()).startsWith("OFFER-");
        assertThat(inserted.getOperatorId()).isEqualTo(10001L);
        assertThat(view.status()).isEqualTo("DRAFT");
    }

    @Test
    void createRejectsDuplicateOfferForApplication() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(offerMapper.selectRowByApplicationId(51001L)).thenReturn(row(92001L, "SENT"));

        assertThatThrownBy(() -> service.create(createForm(), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
        verify(offerMapper, never()).insert(any(Offer.class));
    }

    @Test
    void createRejectsExpireAtInPast() {
        stubApplicationAndJob();
        when(dataScopeService.current()).thenReturn(allScope());
        when(offerMapper.selectRowByApplicationId(51001L)).thenReturn(null);
        OfferCreateForm form = createForm();
        form.setExpireAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.create(form, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void sendMarksSentAndPublishesOfferAndNotification() {
        OfferRow draft = row(92001L, "DRAFT");
        OfferRow sent = row(92001L, "SENT");
        when(offerMapper.selectRowById(92001L)).thenReturn(draft, sent);
        when(dataScopeService.current()).thenReturn(allScope());
        when(offerMapper.markSent(eq(92001L), anyString(), eq(0))).thenReturn(1);
        Application application = application("IN_PROGRESS", 2);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        when(applicationMapper.updateStateWithVersion(any(Application.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        OfferView view = service.send(92001L, sendForm(), null);

        assertThat(view.status()).isEqualTo("SENT");
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(offerMapper).markSent(eq(92001L), hash.capture(), eq(0));
        assertThat(hash.getValue()).hasSize(64);
        assertThat(application.getStatus()).isEqualTo("OFFERED");
        assertThat(application.getCurrentStage()).isEqualTo("OFFER");
        verify(historyMapper).insert(any(ApplicationStageHistory.class));
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofMinutes(10)));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.OFFER_SENT), eq("92001"), eq("offer"),
                payload.capture());
        OfferSentPayload event = (OfferSentPayload) payload.getValue();
        assertThat(event.offerNo()).isEqualTo("OFFER-20261103-ABC123");
        assertThat(event.candidateId()).isEqualTo("60001");

        ArgumentCaptor<Object> notification = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.NOTIFICATION_REQUESTED), eq("92001"),
                eq("offer"), notification.capture());
        NotificationRequestedPayload notify = (NotificationRequestedPayload) notification.getValue();
        assertThat(notify.templateCode()).isEqualTo("offer_send");
        assertThat(notify.channel()).containsExactly("EMAIL", "INAPP");
        assertThat(notify.variables()).doesNotContainKey("token");
    }

    @Test
    void sendIsIdempotentWhenAlreadySent() {
        when(offerMapper.selectRowById(92001L)).thenReturn(row(92001L, "SENT"));
        when(dataScopeService.current()).thenReturn(allScope());

        OfferView view = service.send(92001L, sendForm(), null);

        assertThat(view.status()).isEqualTo("SENT");
        verify(offerMapper, never()).markSent(anyLong(), anyString(), anyInt());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void sendRejectsConfirmedOffer() {
        when(offerMapper.selectRowById(92001L)).thenReturn(row(92001L, "CONFIRMED"));
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.send(92001L, sendForm(), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OFFER_STATE_NOT_ALLOWED);
    }

    @Test
    void approveTrueSendsOffer() {
        OfferRow draft = row(92001L, "DRAFT");
        OfferRow sent = row(92001L, "SENT");
        when(offerMapper.selectRowById(92001L)).thenReturn(draft, sent);
        when(dataScopeService.current()).thenReturn(allScope());
        when(offerMapper.markSent(eq(92001L), anyString(), eq(0))).thenReturn(1);
        when(applicationMapper.selectById(51001L)).thenReturn(application("IN_PROGRESS", 2));
        when(applicationMapper.updateStateWithVersion(any(Application.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        OfferApproveForm form = new OfferApproveForm();
        form.setApproved(true);
        form.setVersion(0);

        OfferView view = service.approve(92001L, form);

        assertThat(view.status()).isEqualTo("SENT");
        verify(offerMapper).markSent(eq(92001L), anyString(), eq(0));
    }

    @Test
    void approveFalseMovesBackToDraft() {
        when(offerMapper.selectRowById(92001L))
                .thenReturn(row(92001L, "PENDING_APPROVAL"), row(92001L, "DRAFT"));
        when(dataScopeService.current()).thenReturn(allScope());
        when(offerMapper.markDraft(92001L, 0)).thenReturn(1);
        OfferApproveForm form = new OfferApproveForm();
        form.setApproved(false);
        form.setComment("薪资需复核");
        form.setVersion(0);

        OfferView view = service.approve(92001L, form);

        assertThat(view.status()).isEqualTo("DRAFT");
        verify(offerMapper).markDraft(92001L, 0);
    }

    @Test
    void withdrawRejectsConfirmedOffer() {
        when(offerMapper.selectRowById(92001L)).thenReturn(row(92001L, "CONFIRMED"));
        when(dataScopeService.current()).thenReturn(allScope());

        assertThatThrownBy(() -> service.withdraw(92001L, "岗位冻结"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OFFER_STATE_NOT_ALLOWED);
        verify(offerMapper, never()).markWithdrawn(anyLong(), anyInt());
    }

    @Test
    void portalDetailRejectsUnknownToken() {
        when(offerMapper.selectRowByTokenHash(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.portalDetail("bad-token"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OFFER_STATE_NOT_ALLOWED);
    }

    @Test
    void portalDetailExpiresOverdueOffer() {
        OfferRow expired = row(92001L, "SENT");
        expired.setExpireAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(5));
        when(offerMapper.selectRowByTokenHash(anyString())).thenReturn(expired);
        when(offerMapper.markExpired(92001L, 0)).thenReturn(1);

        assertThatThrownBy(() -> service.portalDetail("token"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OFFER_STATE_NOT_ALLOWED);
        verify(offerMapper).markExpired(92001L, 0);
        verify(outboxPublisher).publish(eq(EventTypes.OFFER_EXPIRED), eq("92001"),
                eq("offer"), any());
    }

    @Test
    void respondAcceptConfirmsAndHiresCandidate() {
        OfferRow sent = row(92001L, "SENT");
        OfferRow confirmed = row(92001L, "CONFIRMED");
        confirmed.setConfirmedAt(LocalDateTime.now());
        when(offerMapper.selectRowByTokenHash(anyString())).thenReturn(sent);
        when(offerMapper.markConfirmed(92001L, 0)).thenReturn(1);
        when(offerMapper.selectRowById(92001L)).thenReturn(confirmed);
        Application application = application("OFFERED", 3);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        when(applicationMapper.updateStateWithVersion(any(Application.class))).thenReturn(1);

        PortalOfferView view = service.respond("token", respondForm("ACCEPT"));

        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(application.getStatus()).isEqualTo("HIRED");
        verify(historyMapper).insert(any(ApplicationStageHistory.class));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.OFFER_CONFIRMED), eq("92001"), eq("offer"),
                payload.capture());
        OfferConfirmedPayload event = (OfferConfirmedPayload) payload.getValue();
        assertThat(event.entryDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(event.candidateId()).isEqualTo("60001");
    }

    @Test
    void respondAcceptIsIdempotentWhenAlreadyConfirmed() {
        when(offerMapper.selectRowByTokenHash(anyString())).thenReturn(row(92001L, "CONFIRMED"));

        PortalOfferView view = service.respond("token", respondForm("ACCEPT"));

        assertThat(view.status()).isEqualTo("CONFIRMED");
        verify(offerMapper, never()).markConfirmed(anyLong(), anyInt());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void respondDeclineReturnsApplicationToInProgress() {
        OfferRow sent = row(92001L, "SENT");
        OfferRow declined = row(92001L, "DECLINED");
        when(offerMapper.selectRowByTokenHash(anyString())).thenReturn(sent);
        when(offerMapper.markDeclined(92001L, 0)).thenReturn(1);
        when(offerMapper.selectRowById(92001L)).thenReturn(declined);
        Application application = application("OFFERED", 3);
        when(applicationMapper.selectById(51001L)).thenReturn(application);
        when(applicationMapper.updateStateWithVersion(any(Application.class))).thenReturn(1);

        PortalOfferView view = service.respond("token", respondForm("DECLINE"));

        assertThat(view.status()).isEqualTo("DECLINED");
        assertThat(application.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(application.getCurrentStage()).isEqualTo("OFFER");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq(EventTypes.OFFER_DECLINED), eq("92001"), eq("offer"),
                payload.capture());
        OfferDeclinedPayload event = (OfferDeclinedPayload) payload.getValue();
        assertThat(event.reason()).isEqualTo("已接受其他 Offer");
    }

    @Test
    void respondRejectsCandidateNameMismatch() {
        when(offerMapper.selectRowByTokenHash(anyString())).thenReturn(row(92001L, "SENT"));
        OfferRespondForm form = respondForm("ACCEPT");
        form.setName("李四");

        assertThatThrownBy(() -> service.respond("token", form))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void confirmLinkReturnsCachedUrlWithoutRotatingToken() {
        when(offerMapper.selectRowById(92001L)).thenReturn(row(92001L, "SENT"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("offer:confirm:92001")).thenReturn("http://portal?token=cached");

        ConfirmLinkView view = service.confirmLink(92001L);

        assertThat(view.url()).isEqualTo("http://portal?token=cached");
        verify(offerMapper, never()).rotateToken(anyLong(), anyString(), anyInt());
    }

    @Test
    void confirmLinkGeneratesAndRotatesTokenWhenCacheMiss() {
        when(offerMapper.selectRowById(92001L)).thenReturn(row(92001L, "SENT"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("offer:confirm:92001")).thenReturn(null);
        when(offerMapper.rotateToken(eq(92001L), anyString(), eq(0))).thenReturn(1);

        ConfirmLinkView view = service.confirmLink(92001L);

        assertThat(view.url()).contains("token=");
        assertThat(view.expireAt()).isAfter(Instant.now());
        verify(offerMapper).rotateToken(eq(92001L), anyString(), eq(0));
    }

    @Test
    void expireOverduePublishesForEachExpiredOffer() {
        Offer expired = new Offer();
        expired.setId(92001L);
        expired.setVersion(2);
        when(offerMapper.selectExpired(100)).thenReturn(List.of(expired));
        when(offerMapper.markExpired(92001L, 2)).thenReturn(1);
        when(offerMapper.selectRowById(92001L)).thenReturn(row(92001L, "EXPIRED"));

        service.expireOverdue();

        verify(outboxPublisher).publish(eq(EventTypes.OFFER_EXPIRED), eq("92001"),
                eq("offer"), any());
        verify(outboxPublisher).publish(eq(EventTypes.NOTIFICATION_REQUESTED), eq("92001"),
                eq("offer"), any());
    }

    private void stubApplicationAndJob() {
        when(applicationMapper.selectById(51001L)).thenReturn(application("IN_PROGRESS", 2));
        Job job = new Job();
        job.setId(50001L);
        job.setTitle("Java 开发工程师");
        job.setDeptId(30001L);
        job.setOwnerHrId(10001L);
        when(jobMapper.selectById(50001L)).thenReturn(job);
    }

    private static OfferCreateForm createForm() {
        OfferCreateForm form = new OfferCreateForm();
        form.setApplicationId("51001");
        Map<String, Object> salary = new LinkedHashMap<>();
        salary.put("base", 20000);
        salary.put("bonus", "2个月");
        salary.put("currency", "CNY");
        form.setSalary(salary);
        form.setEntryDate(LocalDate.of(2026, 11, 1));
        form.setExpireAt(Instant.now().plusSeconds(86_400));
        Map<String, Object> onboarding = new LinkedHashMap<>();
        onboarding.put("contact", "HR 王女士");
        onboarding.put("documents", List.of("身份证", "学历证明"));
        form.setOnboarding(onboarding);
        return form;
    }

    private static OfferSendForm sendForm() {
        OfferSendForm form = new OfferSendForm();
        form.setChannel(List.of("EMAIL", "INAPP"));
        form.setTemplateCode("offer_send");
        form.setVersion(0);
        return form;
    }

    private static OfferRespondForm respondForm(String decision) {
        OfferRespondForm form = new OfferRespondForm();
        form.setDecision(decision);
        form.setComment("已接受其他 Offer");
        form.setName("张三");
        return form;
    }

    private static Application application(String status, int version) {
        Application application = new Application();
        application.setId(51001L);
        application.setJobId(50001L);
        application.setJobTitle("Java 开发工程师");
        application.setPersonId(60001L);
        application.setPersonName("张三");
        application.setCurrentStage("INTERVIEW");
        application.setRoundNo(1);
        application.setStatus(status);
        application.setVersion(version);
        return application;
    }

    private static OfferRow row(long id, String status) {
        OfferRow row = new OfferRow();
        row.setId(id);
        row.setOfferNo("OFFER-20261103-ABC123");
        row.setApplicationId(51001L);
        row.setPositionTitle("Java 开发工程师");
        row.setSalaryJson("{\"base\":20000,\"currency\":\"CNY\"}");
        row.setEntryDate(LocalDate.of(2026, 11, 1));
        row.setExpireAt(LocalDateTime.now().plusDays(7));
        row.setStatus(status);
        row.setOnboardingJson("{\"contact\":\"HR 王女士\"}");
        row.setOperatorId(10001L);
        row.setJobId(50001L);
        row.setPersonId(60001L);
        row.setPersonName("张三");
        row.setDeptId(30001L);
        row.setOwnerHrId(10001L);
        row.setVersion(0);
        return row;
    }

    private static DataScopeService.DataScope allScope() {
        return new DataScopeService.DataScope("ALL", null, List.of(), 10001L);
    }
}
