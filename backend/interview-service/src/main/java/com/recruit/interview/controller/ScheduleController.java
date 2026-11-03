package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.CalendarEventView;
import com.recruit.interview.dto.CalendarQuery;
import com.recruit.interview.dto.ConflictCheckForm;
import com.recruit.interview.dto.ConflictCheckResult;
import com.recruit.interview.dto.ScheduleBatchForm;
import com.recruit.interview.dto.ScheduleCreateForm;
import com.recruit.interview.dto.SchedulePageQuery;
import com.recruit.interview.dto.ScheduleUpdateForm;
import com.recruit.interview.dto.ScheduleView;
import com.recruit.interview.service.ScheduleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 排期端点（OpenAPI §8）。
 *
 * <p>{@code /schedules/conflict-check} 与 {@code /schedules/batch} 是字面量路径，
 * Spring 会优先于 {@code /schedules/{id}} 匹配，不会把 {@code conflict-check} 当成 ID。</p>
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 排期分页。
     *
     * @param query 分页与过滤条件
     * @return 分页排期
     */
    @GetMapping("/schedules")
    @PreAuthorize("@permissionChecker.has('interview:schedule:view')")
    public Result<PageResult<ScheduleView>> page(@Valid SchedulePageQuery query) {
        return Result.ok(scheduleService.page(query));
    }

    /**
     * 新建排期。
     *
     * @param form 创建入参
     * @param key  Idempotency-Key（可选）
     * @return 新排期
     */
    @PostMapping("/schedules")
    @PreAuthorize("@permissionChecker.has('interview:schedule:create')")
    public Result<ScheduleView> create(
            @Valid @RequestBody ScheduleCreateForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(scheduleService.create(form, key));
    }

    /**
     * 排期详情。
     *
     * @param id 面试 ID
     * @return 排期详情
     */
    @GetMapping("/schedules/{id}")
    @PreAuthorize("@permissionChecker.has('interview:schedule:view')")
    public Result<ScheduleView> detail(@PathVariable long id) {
        return Result.ok(scheduleService.detail(id));
    }

    /**
     * 改期/改面试官。
     *
     * @param id   面试 ID
     * @param form 修改入参
     * @return 修改后的排期
     */
    @PutMapping("/schedules/{id}")
    @PreAuthorize("@permissionChecker.has('interview:schedule:update')")
    public Result<ScheduleView> update(@PathVariable long id,
                                       @Valid @RequestBody ScheduleUpdateForm form) {
        return Result.ok(scheduleService.update(id, form));
    }

    /**
     * 取消排期（幂等）。
     *
     * @param id      面试 ID
     * @param version 期望版本；已取消时可省略
     * @param reason  取消原因
     * @return 取消后的排期
     */
    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("@permissionChecker.has('interview:schedule:cancel')")
    public Result<ScheduleView> cancel(@PathVariable long id,
                                       @RequestParam(required = false) Integer version,
                                       @RequestParam(required = false) String reason) {
        return Result.ok(scheduleService.cancel(id, version, reason));
    }

    /**
     * 冲突预检（只读）。
     *
     * @param form 预检入参
     * @return 冲突结果
     */
    @PostMapping("/schedules/conflict-check")
    @PreAuthorize("@permissionChecker.has('interview:schedule:view')")
    public Result<ConflictCheckResult> conflictCheck(@Valid @RequestBody ConflictCheckForm form) {
        return Result.ok(scheduleService.conflictCheck(form));
    }

    /**
     * 批量排期（整批事务，单批 ≤50）。
     *
     * @param form 批量入参
     * @param key  Idempotency-Key（可选）
     * @return 新建排期
     */
    @PostMapping("/schedules/batch")
    @PreAuthorize("@permissionChecker.has('interview:schedule:batch')")
    public Result<List<ScheduleView>> batch(
            @Valid @RequestBody ScheduleBatchForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return Result.ok(scheduleService.batch(form, key));
    }

    /**
     * 日历查询（只读）。
     *
     * @param query 查询条件
     * @return 日历事件
     */
    @GetMapping("/calendar")
    @PreAuthorize("@permissionChecker.has('interview:schedule:view')")
    public Result<List<CalendarEventView>> calendar(@Valid CalendarQuery query) {
        return Result.ok(scheduleService.calendar(query));
    }
}
