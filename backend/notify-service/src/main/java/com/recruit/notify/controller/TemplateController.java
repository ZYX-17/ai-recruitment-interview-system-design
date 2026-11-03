package com.recruit.notify.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.notify.dto.TemplatePageQuery;
import com.recruit.notify.dto.TemplateUpdateForm;
import com.recruit.notify.dto.TemplateView;
import com.recruit.notify.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知模板端点（OpenAPI §15 N-03/N-04）。
 */
@RestController
@RequestMapping("/api/notify/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    /**
     * 模板分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('system:notice:view')")
    public Result<PageResult<TemplateView>> page(@Valid TemplatePageQuery query) {
        return Result.ok(templateService.page(query));
    }

    /**
     * 修改模板（新增版本）。
     *
     * @param id   当前版本行 ID
     * @param form 新内容
     * @return 新版本
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('system:notice:update')")
    public Result<TemplateView> update(@PathVariable long id, @Valid @RequestBody TemplateUpdateForm form) {
        return Result.ok(templateService.update(id, form));
    }
}
