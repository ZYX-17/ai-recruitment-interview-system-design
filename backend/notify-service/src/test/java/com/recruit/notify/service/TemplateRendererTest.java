package com.recruit.notify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.notify.entity.NotificationTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模板渲染器单测。
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer(new ObjectMapper());

    @Test
    void rendersDeclaredVariables() {
        RenderedTemplate rendered = renderer.render(template(
                        "【面试邀约】{{jobTitle}}",
                        "{{candidateName}} 您好，时间：{{startTime}}",
                        "[\"candidateName\",\"jobTitle\",\"startTime\"]"),
                Map.of("candidateName", "张三", "jobTitle", "Java", "startTime", "2026-10-20 09:00"));

        assertThat(rendered.title()).isEqualTo("【面试邀约】Java");
        assertThat(rendered.content()).isEqualTo("张三 您好，时间：2026-10-20 09:00");
    }

    @Test
    void missingVariableFailsWithNtf6001() {
        assertThatThrownBy(() -> renderer.render(template(
                        "【面试】{{jobTitle}}", "{{candidateName}} 您好",
                        "[\"candidateName\",\"jobTitle\"]"),
                Map.of("jobTitle", "Java")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("NTF6001");
    }

    @Test
    void placeholderOutsideWhitelistFails() {
        assertThatThrownBy(() -> renderer.render(template(
                        "{{jobTitle}}", "{{candidateName}}",
                        "[\"jobTitle\"]"),
                Map.of("jobTitle", "Java", "candidateName", "张三")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("NTF6001");
    }

    @Test
    void malformedPlaceholderFails() {
        assertThatThrownBy(() -> renderer.resolveVariables("{{jobTitle}", "OK", List.of("jobTitle")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo("NTF6001");
    }

    @Test
    void emptyWhitelistDerivesFromPlaceholders() {
        assertThat(renderer.resolveVariables("{{a}}", "{{b}}", List.of()))
                .containsExactly("a", "b");
    }

    private static NotificationTemplate template(String title, String content, String variablesJson) {
        NotificationTemplate template = new NotificationTemplate();
        template.setId(1L);
        template.setTemplateCode("interview_invite");
        template.setChannel("INAPP");
        template.setTitleTpl(title);
        template.setContentTpl(content);
        template.setVariablesJson(variablesJson);
        template.setStatus("ACTIVE");
        return template;
    }
}
