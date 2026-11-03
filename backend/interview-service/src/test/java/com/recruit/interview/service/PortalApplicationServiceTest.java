package com.recruit.interview.service;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.idempotency.IdempotencyService;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.ApplicationCreateForm;
import com.recruit.interview.dto.ApplicationView;
import com.recruit.interview.dto.PortalApplicationCreateForm;
import com.recruit.interview.dto.PortalApplicationPageQuery;
import com.recruit.interview.dto.PortalApplicationView;
import com.recruit.interview.entity.Application;
import com.recruit.interview.mapper.ApplicationMapper;
import org.junit.jupiter.api.AfterEach;
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
 * 门户“我的投递”（I-09）。
 */
@ExtendWith(MockitoExtension.class)
class PortalApplicationServiceTest {

    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationService applicationService;
    @Mock
    private IdempotencyService idempotencyService;

    private PortalApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PortalApplicationService(applicationMapper, applicationService, idempotencyService);
        UserContext.set("10006", List.of("CANDIDATE"), "候选人-孙", null);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void pageUsesCurrentCandidateOnly() {
        when(applicationMapper.countPortalPage(10006L, "IN_PROGRESS")).thenReturn(1L);
        when(applicationMapper.selectPortalPage(10006L, "IN_PROGRESS", 20, 0L))
                .thenReturn(List.of(application()));
        PortalApplicationPageQuery query = new PortalApplicationPageQuery();
        query.setStatus("in_progress");

        PageResult<PortalApplicationView> page = service.page(query);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getList().get(0).jobTitle()).isEqualTo("Java 开发工程师");
        verify(applicationMapper).countPortalPage(10006L, "IN_PROGRESS");
    }

    @Test
    void nonCandidateIsForbidden() {
        UserContext.set("10001", List.of("HR_ADMIN"));

        assertThatThrownBy(() -> service.page(new PortalApplicationPageQuery()))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN));
        verify(applicationMapper, never()).countPortalPage(anyLong(), any());
    }

    @Test
    void createBindsPersonIdAndRemarkFromCurrentUser() {
        when(applicationService.create(any(ApplicationCreateForm.class), isNull(), eq("内推"))).thenReturn(view());
        PortalApplicationCreateForm form = new PortalApplicationCreateForm();
        form.setJobId("50001");
        form.setRemark("内推");

        PortalApplicationView created = service.create(form, null);

        assertThat(created.id()).isEqualTo("51001");
        ArgumentCaptor<ApplicationCreateForm> inner = ArgumentCaptor.forClass(ApplicationCreateForm.class);
        verify(applicationService).create(inner.capture(), isNull(), eq("内推"));
        assertThat(inner.getValue().getPersonId()).isEqualTo("10006");
        assertThat(inner.getValue().getPersonName()).isEqualTo("候选人-孙");
        assertThat(inner.getValue().getSource()).isEqualTo("PORTAL");
        verify(idempotencyService, never()).execute(any(), any(), any(Class.class), any());
    }

    @Test
    void createWithKeyUsesPortalScope() {
        PortalApplicationCreateForm form = new PortalApplicationCreateForm();
        form.setJobId("50001");
        PortalApplicationView expected = new PortalApplicationView("51001", "50001", "Java 开发工程师",
                "IN_PROGRESS", "SCREEN", 0, "PORTAL", null, null, null, 0);
        when(idempotencyService.execute(eq("interview.portal.application.create"), eq("key-1"),
                eq(PortalApplicationView.class), any())).thenReturn(expected);

        PortalApplicationView created = service.create(form, "key-1");

        assertThat(created).isSameAs(expected);
        verify(applicationService, never()).create(any(), any(), any());
    }

    @Test
    void withdrawChecksOwnershipThroughDomainService() {
        when(applicationService.withdrawForCandidate(51001L, 10006L)).thenReturn(view());

        PortalApplicationView result = service.withdraw(51001L);

        assertThat(result.status()).isEqualTo("WITHDRAWN");
        verify(applicationService).withdrawForCandidate(51001L, 10006L);
    }

    private static Application application() {
        Application application = new Application();
        application.setId(51001L);
        application.setJobId(50001L);
        application.setJobTitle("Java 开发工程师");
        application.setPersonId(10006L);
        application.setStatus("IN_PROGRESS");
        application.setCurrentStage("SCREEN");
        application.setRoundNo(0);
        application.setSource("PORTAL");
        application.setVersion(0);
        application.setCreatedAt(LocalDateTime.of(2026, 10, 1, 1, 0));
        application.setUpdatedAt(LocalDateTime.of(2026, 10, 1, 1, 0));
        return application;
    }

    private static ApplicationView view() {
        return new ApplicationView("51001", "50001", "Java 开发工程师", "10006", "候选人-孙",
                "SCREEN", 0, List.of(), null, "WITHDRAWN", null, "PORTAL",
                null, null, 1);
    }
}
