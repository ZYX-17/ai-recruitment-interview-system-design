package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.MinutesReviewForm;
import com.recruit.interview.dto.MinutesView;
import com.recruit.interview.service.MinutesService;
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
 * 纪要查询与人工确认（OpenAPI §10、X-04/X-05）。
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class MinutesController {

    private final MinutesService minutesService;

    /**
     * 场次纪要详情（HR/本场面试官）。
     *
     * @param id 面试 ID
     * @return 纪要
     */
    @GetMapping("/schedules/{id}/minutes")
    @PreAuthorize("@permissionChecker.has('interview:minutes:view')")
    public Result<MinutesView> detail(@PathVariable long id) {
        return Result.ok(minutesService.detail(id));
    }

    /**
     * 确认/驳回纪要。
     *
     * @param id   纪要 ID
     * @param form 复核入参
     * @return 复核后的纪要
     */
    @PutMapping("/minutes/{id}/confirm")
    @PreAuthorize("@permissionChecker.has('interview:minutes:confirm')")
    public Result<MinutesView> confirm(@PathVariable long id,
                                       @Valid @RequestBody MinutesReviewForm form) {
        return Result.ok(minutesService.review(id, form));
    }
}
