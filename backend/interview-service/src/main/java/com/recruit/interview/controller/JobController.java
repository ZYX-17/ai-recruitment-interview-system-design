package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.JobCreateForm;
import com.recruit.interview.dto.JobPageQuery;
import com.recruit.interview.dto.JobUpdateForm;
import com.recruit.interview.dto.JobView;
import com.recruit.interview.dto.VersionForm;
import com.recruit.interview.service.JobService;
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
 * 岗位端点（OpenAPI §6）。
 *
 * <p>权限码由 auth-service 内部接口现查；发布/更新/关闭的 Outbox 写入在 Service 事务内完成。</p>
 */
@RestController
@RequestMapping("/api/interview/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * 岗位分页。
     *
     * @param query 分页与过滤条件
     * @return 分页岗位
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('interview:job:view')")
    public Result<PageResult<JobView>> page(@Valid JobPageQuery query) {
        return Result.ok(jobService.page(query));
    }

    /**
     * 岗位详情。
     *
     * @param id 岗位 ID
     * @return 岗位详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:job:view')")
    public Result<JobView> detail(@PathVariable long id) {
        return Result.ok(jobService.detail(id));
    }

    /**
     * 新增岗位。
     *
     * @param form 新增入参
     * @param key  Idempotency-Key（可选）
     * @return 新岗位
     */
    @PostMapping
    @PreAuthorize("@permissionChecker.has('interview:job:create')")
    public Result<JobView> create(@Valid @RequestBody JobCreateForm form,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(jobService.create(form, key));
    }

    /**
     * 修改岗位。
     *
     * @param id   岗位 ID
     * @param form 修改入参
     * @return 更新后的岗位
     */
    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:job:update')")
    public Result<JobView> update(@PathVariable long id, @Valid @RequestBody JobUpdateForm form) {
        return Result.ok(jobService.update(id, form));
    }

    /**
     * 发布岗位。
     *
     * @param id   岗位 ID
     * @param form 版本号
     * @return 发布后的岗位
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("@permissionChecker.has('interview:job:publish')")
    public Result<JobView> publish(@PathVariable long id, @Valid @RequestBody VersionForm form) {
        return Result.ok(jobService.publish(id, form.getVersion()));
    }

    /**
     * 关闭岗位。
     *
     * @param id   岗位 ID
     * @param form 版本号
     * @return 关闭后的岗位
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("@permissionChecker.has('interview:job:close')")
    public Result<JobView> close(@PathVariable long id, @Valid @RequestBody VersionForm form) {
        return Result.ok(jobService.close(id, form.getVersion()));
    }

    /**
     * 删除岗位（仅草稿且无投递）。
     *
     * @param id 岗位 ID
     * @return 统一响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('interview:job:delete')")
    public Result<Void> delete(@PathVariable long id) {
        jobService.delete(id);
        return Result.ok();
    }
}
