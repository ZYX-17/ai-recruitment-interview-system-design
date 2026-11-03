package com.recruit.notify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.notify.dto.TemplatePageQuery;
import com.recruit.notify.dto.TemplateUpdateForm;
import com.recruit.notify.entity.NotificationTemplate;
import com.recruit.notify.mapper.NotificationTemplateMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模板管理单测。
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private NotificationTemplateMapper templateMapper;

    private TemplateService service;

    @BeforeEach
    void setUp() {
        service = new TemplateService(templateMapper, new TemplateRenderer(new ObjectMapper()), new ObjectMapper());
    }

    @Test
    void updateCreatesNextVersionAndArchivesOld() {
        NotificationTemplate current = template(10L, 1, "ACTIVE");
        when(templateMapper.selectById(10L)).thenReturn(current);
        when(templateMapper.archive(10L, 1)).thenReturn(1);
        when(templateMapper.insert(any(NotificationTemplate.class))).thenAnswer(invocation -> {
            NotificationTemplate next = invocation.getArgument(0);
            next.setId(11L);
            return 1;
        });

        TemplateUpdateForm form = new TemplateUpdateForm();
        form.setTitleTemplate("【改版】{{jobTitle}}");
        form.setContentTemplate("{{candidateName}} 您好");
        form.setVariables(List.of("candidateName", "jobTitle"));
        form.setVersion(1);

        assertThat(service.update(10L, form).version()).isEqualTo(2);
        verify(templateMapper).archive(10L, 1);
        ArgumentCaptor<NotificationTemplate> captor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templateMapper).insert(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getVariablesJson()).contains("candidateName");
    }

    @Test
    void versionMismatchFailsWithSys412() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 2, "ACTIVE"));

        TemplateUpdateForm form = form(1);
        assertThatThrownBy(() -> service.update(10L, form))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("SYS412");
        verify(templateMapper, never()).archive(anyLong(), anyInt());
    }

    @Test
    void placeholderOutsideWhitelistFailsBeforeArchive() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 1, "ACTIVE"));
        TemplateUpdateForm form = form(1);
        form.setVariables(List.of("candidateName"));

        assertThatThrownBy(() -> service.update(10L, form))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("NTF6001");
        verify(templateMapper, never()).archive(anyLong(), anyInt());
    }

    @Test
    void archiveCasFailureFailsWithSys412() {
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 1, "ACTIVE"));
        when(templateMapper.archive(10L, 1)).thenReturn(0);

        assertThatThrownBy(() -> service.update(10L, form(1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("SYS412");
        verify(templateMapper, never()).insert(any(NotificationTemplate.class));
    }

    @Test
    void pageMapsTemplates() {
        TemplatePageQuery query = new TemplatePageQuery();
        query.setTemplateCode("offer_send");
        when(templateMapper.countTemplatePage("offer_send", null, null)).thenReturn(1L);
        when(templateMapper.selectTemplatePage("offer_send", null, null, 20, 0L))
                .thenReturn(List.of(template(10L, 1, "ACTIVE")));

        assertThat(service.page(query).getList()).hasSize(1);
        assertThat(service.page(query).getList().get(0).templateCode()).isEqualTo("interview_invite");
    }

    private static TemplateUpdateForm form(int version) {
        TemplateUpdateForm form = new TemplateUpdateForm();
        form.setTitleTemplate("【面试】{{jobTitle}}");
        form.setContentTemplate("{{candidateName}} 您好");
        form.setVariables(List.of("candidateName", "jobTitle"));
        form.setVersion(version);
        return form;
    }

    private static NotificationTemplate template(long id, int version, String status) {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(id);
        template.setTemplateCode("interview_invite");
        template.setChannel("INAPP");
        template.setVersion(version);
        template.setTitleTpl("【面试】{{jobTitle}}");
        template.setContentTpl("{{candidateName}} 您好");
        template.setVariablesJson("[\"candidateName\",\"jobTitle\"]");
        template.setStatus(status);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        return template;
    }
}
