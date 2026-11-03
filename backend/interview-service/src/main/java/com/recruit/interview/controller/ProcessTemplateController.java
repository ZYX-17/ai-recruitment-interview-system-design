package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.ProcessTemplateCreateForm;
import com.recruit.interview.dto.ProcessTemplatePageQuery;
import com.recruit.interview.dto.ProcessTemplateUpdateForm;
import com.recruit.interview.dto.ProcessTemplateView;
import com.recruit.interview.service.ProcessTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 岗位流程模板端点（OpenAPI §6）。
 */
@RestController
@RequestMapping("/api/interview/process-templates")
@RequiredArgsConstructor
public class ProcessTemplateController {

    private final ProcessTemplateService processTemplateService;

    /**
     * 模板分页。
     *
     * @param query 分页与过滤条件
     * @return 分页模板
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('interview:process:view')")
    public Result<PageResult<ProcessTemplateView>> page(@Valid ProcessTemplatePageQuery query) {
        return Result.ok(processTemplateService.page(query));
    }

    /**
     * 模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:process:view')")
    public Result<ProcessTemplateView> detail(@PathVariable long id) {
        return Result.ok(processTemplateService.detail(id));
    }

    /**
     * 新增模板（自动生成下一个版本并激活）。
     *
     * @param form 新增入参
     * @param key  Idempotency-Key（可选）
     * @return 新模板
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('interview:process:create')")
    public Result<ProcessTemplateView> create(
            @Valid @RequestBody ProcessTemplateCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(processTemplateService.create(form, key));
    }

    /**
     * 修改模板（生成新版本，旧版本只读）。
     *
     * @param id   模板 ID
     * @param form 修改入参
     * @return 新版本模板
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:process:update')")
    public Result<ProcessTemplateView> update(@PathVariable long id,
                                              @Valid @RequestBody ProcessTemplateUpdateForm form) {
        return Result.ok(processTemplateService.update(id, form));
    }

    /**
     * 删除模板（岗位已有投递时拒绝）。
     *
     * @param id 模板 ID
     * @return 统一响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:process:delete')")
    public Result<Void> delete(@PathVariable long id) {
        processTemplateService.delete(id);
        return Result.ok();
    }
}
