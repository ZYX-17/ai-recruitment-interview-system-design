package com.recruit.notify.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.notify.entity.NotificationTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染器。
 *
 * <p>规则：占位符必须在模板声明的变量白名单中；任一必填变量缺失或为空都报
 * {@code NTF6001}，不输出半成品。</p>
 */
@Component
@RequiredArgsConstructor
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*\\}\\}");

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * 渲染模板。
     *
     * @param template 模板实体
     * @param variables 变量值
     * @return 渲染结果
     * @throws BizException 模板不存在、变量缺失或白名单不一致
     */
    public RenderedTemplate render(NotificationTemplate template, Map<String, Object> variables) {
        if (template == null) {
            throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "通知模板不存在");
        }
        List<String> declared = readVariables(template.getVariablesJson());
        List<String> required = resolveVariables(template.getTitleTpl(), template.getContentTpl(), declared);

        for (String name : required) {
            Object value = variables == null ? null : variables.get(name);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID,
                        "模板变量缺失: " + name);
            }
        }
        String title = substitute(template.getTitleTpl(), variables);
        String content = substitute(template.getContentTpl(), variables);
        return new RenderedTemplate(title, content);
    }

    /**
     * 校验并返回模板必填变量（供模板保存时预校验）。
     *
     * @param titleTpl   标题模板
     * @param contentTpl 正文模板
     * @param declared   声明的变量白名单
     * @return 必填变量列表
     */
    public List<String> resolveVariables(String titleTpl, String contentTpl, List<String> declared) {
        Set<String> placeholders = new LinkedHashSet<>();
        collect(titleTpl, placeholders);
        collect(contentTpl, placeholders);
        Set<String> whitelist = declared == null ? new LinkedHashSet<>() : new LinkedHashSet<>(declared);
        if (whitelist.isEmpty()) {
            whitelist.addAll(placeholders);
        }
        for (String placeholder : placeholders) {
            if (!whitelist.contains(placeholder)) {
                throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID,
                        "模板占位符不在变量白名单中: " + placeholder);
            }
        }
        return new ArrayList<>(whitelist);
    }

    private void collect(String text, Set<String> target) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            target.add(matcher.group(1));
        }
        String stripped = PLACEHOLDER.matcher(text).replaceAll("");
        if (stripped.contains("{{") || stripped.contains("}}")) {
            throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "模板占位符格式错误");
        }
    }

    private String substitute(String text, Map<String, Object> variables) {
        if (text == null || variables == null || variables.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private List<String> readVariables(String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(variablesJson, STRING_LIST);
            return values == null ? List.of() : values;
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "模板变量定义不是合法 JSON", e);
        }
    }
}
