package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.FunnelView;
import com.recruit.interview.dto.StatsQuery;
import com.recruit.interview.dto.TimeToHireView;
import com.recruit.interview.dto.WorkloadView;
import com.recruit.interview.service.StatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 招聘统计端点（OpenAPI §8，P11）。
 */
@RestController
@RequestMapping("/api/interview/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 招聘漏斗。
     *
     * @param query 岗位与时间范围
     * @return 阶段计数与转化率
     */
    @GetMapping("/funnel")
    @PreAuthorize("@permissionChecker.has('interview:stats:view')")
    public Result<FunnelView> funnel(@Valid StatsQuery query) {
        return Result.ok(statsService.funnel(query));
    }

    /**
     * 面试官工作量。
     *
     * @param query 时间范围与面试官过滤
     * @return 面试官维度统计
     */
    @GetMapping("/workload")
    @PreAuthorize("@permissionChecker.has('interview:stats:view')")
    public Result<WorkloadView> workload(@Valid StatsQuery query) {
        return Result.ok(statsService.workload(query));
    }

    /**
     * 招聘周期。
     *
     * @param query 岗位与时间范围
     * @return 平均/最短/最长天数
     */
    @GetMapping("/time-to-hire")
    @PreAuthorize("@permissionChecker.has('interview:stats:view')")
    public Result<TimeToHireView> timeToHire(@Valid StatsQuery query) {
        return Result.ok(statsService.timeToHire(query));
    }
}
