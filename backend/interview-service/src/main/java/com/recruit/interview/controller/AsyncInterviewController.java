package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.AsyncAnswerView;
import com.recruit.interview.dto.AsyncInviteLinkView;
import com.recruit.interview.dto.AsyncPaperCreateForm;
import com.recruit.interview.dto.AsyncPaperPageQuery;
import com.recruit.interview.dto.AsyncPaperUpdateForm;
import com.recruit.interview.dto.AsyncPaperView;
import com.recruit.interview.dto.AsyncPublishView;
import com.recruit.interview.dto.AsyncQuestionCreateForm;
import com.recruit.interview.dto.AsyncQuestionView;
import com.recruit.interview.dto.AsyncReviewForm;
import com.recruit.interview.dto.VersionForm;
import com.recruit.interview.service.AsyncInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异步面试管理端（OpenAPI §12、X-06）。
 *
 * <p>回看与人工复核复用已 seed 的 {@code interview:async:view/update}，
 * 不新增权限码；所有写操作都有 {@code version} CAS。</p>
 */
@RestController
@RequestMapping("/api/interview/async-interviews")
@RequiredArgsConstructor
public class AsyncInterviewController {

    private final AsyncInterviewService service;

    /**
     * 试卷分页。
     *
     * @param query 分页与过滤条件
     * @return 分页试卷
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('interview:async:view')")
    public Result<PageResult<AsyncPaperView>> page(@Valid AsyncPaperPageQuery query) {
        return Result.ok(service.page(query));
    }

    /**
     * 创建试卷。
     *
     * @param form 创建入参
     * @param key  Idempotency-Key（可选）
     * @return 新试卷
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('interview:async:create')")
    public Result<AsyncPaperView> create(
            @Valid @RequestBody AsyncPaperCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(service.create(form, key));
    }

    /**
     * 试卷详情（含题目）。
     *
     * @param id 试卷 ID
     * @return 详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:async:view')")
    public Result<AsyncPaperView> detail(@PathVariable long id) {
        return Result.ok(service.detail(id));
    }

    /**
     * 修改 DRAFT 试卷。
     *
     * @param id   试卷 ID
     * @param form 修改入参
     * @return 修改后的试卷
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:async:update')")
    public Result<AsyncPaperView> update(@PathVariable long id,
                                         @Valid @RequestBody AsyncPaperUpdateForm form) {
        return Result.ok(service.update(id, form));
    }

    /**
     * 发布试卷并生成一次性邀请链接。
     *
     * @param id   试卷 ID
     * @param form 版本号
     * @return 试卷 + 邀请链接
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("@permissionChecker.has('interview:async:publish')")
    public Result<AsyncPublishView> publish(@PathVariable long id,
                                            @Valid @RequestBody VersionForm form) {
        return Result.ok(service.publish(id, form.getVersion()));
    }

    /**
     * 新增题目（仅 DRAFT）。
     *
     * @param id   试卷 ID
     * @param form 题目入参
     * @param key  Idempotency-Key（可选）
     * @return 新题目
     */
    @PostMapping("/{id}/questions")
    @PreAuthorize("@permissionChecker.has('interview:async:update')")
    public Result<AsyncQuestionView> addQuestion(
            @PathVariable long id,
            @Valid @RequestBody AsyncQuestionCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(service.addQuestion(id, form, key));
    }

    /**
     * 重新生成邀请链接（旧链接立即失效）。
     *
     * @param id   试卷 ID
     * @param form 版本号
     * @return 新链接
     */
    @PostMapping("/{id}/invite-link")
    @PreAuthorize("@permissionChecker.has('interview:async:publish')")
    public Result<AsyncInviteLinkView> inviteLink(@PathVariable long id,
                                                  @Valid @RequestBody VersionForm form) {
        return Result.ok(service.inviteLink(id, form.getVersion()));
    }

    /**
     * 回看作答（含转写/AI 分/人工分）。
     *
     * @param id 试卷 ID
     * @return 作答列表
     */
    @GetMapping("/{id}/answers")
    @PreAuthorize("@permissionChecker.has('interview:async:view')")
    public Result<List<AsyncAnswerView>> answers(@PathVariable long id) {
        return Result.ok(service.answers(id));
    }

    /**
     * 人工复核单题。
     *
     * @param id       试卷 ID
     * @param answerId 作答 ID
     * @param form     复核入参
     * @return 复核后的作答
     */
    @PutMapping("/{id}/answers/{answerId}/review")
    @PreAuthorize("@permissionChecker.has('interview:async:update')")
    public Result<AsyncAnswerView> review(@PathVariable long id,
                                          @PathVariable long answerId,
                                          @Valid @RequestBody AsyncReviewForm form) {
        return Result.ok(service.review(id, answerId, form));
    }

    /**
     * HR 重置作答（审计）。
     *
     * @param id   试卷 ID
     * @param form 版本号
     * @return 重置后的试卷
     */
    @PostMapping("/{id}/reset")
    @PreAuthorize("@permissionChecker.has('interview:async:update')")
    public Result<AsyncPaperView> reset(@PathVariable long id,
                                        @Valid @RequestBody VersionForm form) {
        return Result.ok(service.reset(id, form.getVersion()));
    }
}
