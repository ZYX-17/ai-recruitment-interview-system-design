package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.AiDraftView;
import com.recruit.interview.dto.EvaluationComparisonView;
import com.recruit.interview.dto.EvaluationReopenForm;
import com.recruit.interview.dto.EvaluationSubmitForm;
import com.recruit.interview.dto.EvaluationTemplateCreateForm;
import com.recruit.interview.dto.EvaluationTemplatePageQuery;
import com.recruit.interview.dto.EvaluationTemplateUpdateForm;
import com.recruit.interview.dto.EvaluationTemplateView;
import com.recruit.interview.dto.EvaluationView;
import com.recruit.interview.dto.MyEvaluationTaskView;
import com.recruit.interview.service.EvaluationService;
import com.recruit.interview.service.EvaluationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 评价端点（OpenAPI §9，I-16~I-21、X-07）。
 *
 * <p>{@code /evaluations/my} 是字面量路径，优先于 {@code /evaluations/{id}/reopen} 的变量匹配。</p>
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationTemplateService templateService;
    private final EvaluationService evaluationService;

    /**
     * 评价模板分页。
     *
     * @param query 分页与过滤条件
     * @return 分页模板
     */
    @GetMapping("/evaluation-templates")
    @PreAuthorize("@permissionChecker.has('interview:evaluation-template:view')")
    public Result<PageResult<EvaluationTemplateView>> page(@Valid EvaluationTemplatePageQuery query) {
        return Result.ok(templateService.page(query));
    }

    /**
     * 新增评价模板。
     *
     * @param form 新增入参
     * @param key  Idempotency-Key（可选）
     * @return 新模板
     */
    @PostMapping("/evaluation-templates")
    @PreAuthorize("@permissionChecker.has('interview:evaluation-template:create')")
    public Result<EvaluationTemplateView> create(
            @Valid @RequestBody EvaluationTemplateCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(templateService.create(form, key));
    }

    /**
     * 评价模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    @GetMapping("/evaluation-templates/{id}")
    @PreAuthorize("@permissionChecker.has('interview:evaluation-template:view')")
    public Result<EvaluationTemplateView> detail(@PathVariable long id) {
        return Result.ok(templateService.detail(id));
    }

    /**
     * 修改评价模板（生成新版本）。
     *
     * @param id   模板 ID
     * @param form 修改入参
     * @return 新版本
     */
    @PutMapping("/evaluation-templates/{id}")
    @PreAuthorize("@permissionChecker.has('interview:evaluation-template:update')")
    public Result<EvaluationTemplateView> update(@PathVariable long id,
                                                 @Valid @RequestBody EvaluationTemplateUpdateForm form) {
        return Result.ok(templateService.update(id, form));
    }

    /**
     * 删除评价模板（未被历史评价引用时）。
     *
     * @param id 模板 ID
     * @return 空结果
     */
    @DeleteMapping("/evaluation-templates/{id}")
    @PreAuthorize("@permissionChecker.has('interview:evaluation-template:delete')")
    public Result<Void> delete(@PathVariable long id) {
        templateService.delete(id);
        return Result.ok();
    }

    /**
     * 场次评价列表。
     *
     * @param id 面试 ID
     * @return 评价列表
     */
    @GetMapping("/schedules/{id}/evaluations")
    @PreAuthorize("@permissionChecker.has('interview:evaluation:view')")
    public Result<List<EvaluationView>> list(@PathVariable long id) {
        return Result.ok(evaluationService.list(id));
    }

    /**
     * 提交评价（仅本人）。
     *
     * @param id   面试 ID
     * @param form 提交入参
     * @param key  Idempotency-Key（可选）
     * @return 评价
     */
    @PostMapping("/schedules/{id}/evaluations")
    @PreAuthorize("@permissionChecker.has('interview:evaluation:submit')")
    public Result<EvaluationView> submit(
            @PathVariable long id,
            @Valid @RequestBody EvaluationSubmitForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(evaluationService.submit(id, form, key));
    }

    /**
     * 我的评价任务。
     *
     * @param dateFrom 开始日期（可选）
     * @param dateTo   结束日期（可选）
     * @return 任务列表
     */
    @GetMapping("/evaluations/my")
    @PreAuthorize("@permissionChecker.has('interview:evaluation:view')")
    public Result<List<MyEvaluationTaskView>> my(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(evaluationService.myTasks(dateFrom, dateTo));
    }

    /**
     * 多面试官对比。
     *
     * @param id 面试 ID
     * @return 对比结果
     */
    @GetMapping("/schedules/{id}/evaluations/comparison")
    @PreAuthorize("@permissionChecker.has('interview:evaluation:view')")
    public Result<EvaluationComparisonView> comparison(@PathVariable long id) {
        return Result.ok(evaluationService.comparison(id));
    }

    /**
     * AI 评价草稿（只读）。
     *
     * @param id 面试 ID
     * @return 草稿
     */
    @GetMapping("/schedules/{id}/evaluations/ai-draft")
    @PreAuthorize("@permissionChecker.has('interview:evaluation:view')")
    public Result<AiDraftView> aiDraft(@PathVariable long id) {
        return Result.ok(evaluationService.aiDraft(id));
    }

    /**
     * 重开已提交评价（仅 HR_ADMIN）。
     *
     * @param id   评价 ID
     * @param form 重开入参
     * @return 重开后的评价
     */
    @PutMapping("/evaluations/{id}/reopen")
    @PreAuthorize("@permissionChecker.has('interview:evaluation:reopen')")
    public Result<EvaluationView> reopen(@PathVariable long id,
                                         @Valid @RequestBody EvaluationReopenForm form) {
        return Result.ok(evaluationService.reopen(id, form));
    }
}
