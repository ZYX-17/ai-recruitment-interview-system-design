package com.recruit.notify.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.notify.dto.TemplatePageQuery;
import com.recruit.notify.dto.TemplateUpdateForm;
import com.recruit.notify.dto.TemplateView;
import com.recruit.notify.entity.NotificationTemplate;
import com.recruit.notify.mapper.NotificationTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知模板管理。
 *
 * <p>版本只增不改：修改时先把旧行置 ARCHIVED，再插入 version+1 的新行，
 * 历史模板保留供审计与回溯。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final NotificationTemplateMapper templateMapper;
    private final TemplateRenderer templateRenderer;
    private final ObjectMapper objectMapper;

    /**
     * 模板分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<TemplateView> page(TemplatePageQuery query) {
        long total = templateMapper.countTemplatePage(trimToNull(query.getTemplateCode()),
                trimToNull(query.getChannel()), trimToNull(query.getStatus()));
        if (total == 0) {
            return PageResult.empty(query.getPageNum(), query.getPageSize());
        }
        List<NotificationTemplate> rows = templateMapper.selectTemplatePage(
                trimToNull(query.getTemplateCode()), trimToNull(query.getChannel()),
                trimToNull(query.getStatus()), query.getPageSize(),
                (long) (query.getPageNum() - 1) * query.getPageSize());
        List<TemplateView> views = new ArrayList<>(rows.size());
        for (NotificationTemplate row : rows) {
            views.add(toView(row));
        }
        return PageResult.of(views, total, query.getPageNum(), query.getPageSize());
    }

    /**
     * 修改模板（新增版本）。
     *
     * @param id   当前版本行 ID
     * @param form 新内容与当前 version
     * @return 新版本
     */
    @Transactional
    public TemplateView update(long id, TemplateUpdateForm form) {
        NotificationTemplate current = templateMapper.selectById(id);
        if (current == null) {
            throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "通知模板不存在");
        }
        if (current.getVersion() == null || current.getVersion() != form.getVersion()) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }
        if (!"ACTIVE".equals(current.getStatus())) {
            throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "仅当前生效版本可修改");
        }
        List<String> variables = templateRenderer.resolveVariables(
                form.getTitleTemplate(), form.getContentTemplate(), form.getVariables());
        if (templateMapper.archive(id, form.getVersion()) == 0) {
            throw new BizException(ErrorCode.VERSION_CONFLICT);
        }

        NotificationTemplate next = new NotificationTemplate();
        next.setTemplateCode(current.getTemplateCode());
        next.setChannel(current.getChannel());
        next.setVersion(form.getVersion() + 1);
        next.setTitleTpl(form.getTitleTemplate());
        next.setContentTpl(form.getContentTemplate());
        next.setVariablesJson(writeVariables(variables));
        next.setStatus("ACTIVE");
        templateMapper.insert(next);

        log.info("audit action=NOTIFY_TEMPLATE_UPDATE operatorId={} templateCode={} channel={} oldVersion={} newVersion={}",
                UserContext.getUserId(), next.getTemplateCode(), next.getChannel(),
                form.getVersion(), next.getVersion());
        return toView(next);
    }

    private TemplateView toView(NotificationTemplate row) {
        return new TemplateView(
                row.getId() == null ? null : String.valueOf(row.getId()),
                row.getTemplateCode(),
                row.getChannel(),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getTitleTpl(),
                row.getContentTpl(),
                readVariables(row.getVariablesJson()),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private List<String> readVariables(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            return values == null ? List.of() : values;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeVariables(List<String> variables) {
        try {
            return objectMapper.writeValueAsString(variables == null ? List.of() : variables);
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOTIFY_TEMPLATE_INVALID, "模板变量序列化失败", e);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
