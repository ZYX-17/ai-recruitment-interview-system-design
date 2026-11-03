package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.PortalInterviewJoinView;
import com.recruit.interview.dto.PortalInterviewPageQuery;
import com.recruit.interview.dto.PortalInterviewView;
import com.recruit.interview.service.PortalInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户“我的面试”与进房。
 *
 * <p>join 只做归属 + 时间窗校验并返回房间标识；入会 token 仍由 room-service 签发。</p>
 */
@RestController
@RequestMapping("/api/interview/portal/interviews")
@RequiredArgsConstructor
public class PortalInterviewController {

    private final PortalInterviewService portalInterviewService;

    /**
     * 我的面试分页。
     *
     * @param query 分页与状态
     * @return 分页面试
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.has('portal:interview:view')")
    public Result<PageResult<PortalInterviewView>> page(@Valid PortalInterviewPageQuery query) {
        return Result.ok(portalInterviewService.page(query));
    }

    /**
     * 面试详情。
     *
     * @param id 面试 ID
     * @return 面试详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.has('portal:interview:view')")
    public Result<PortalInterviewView> detail(@PathVariable long id) {
        return Result.ok(portalInterviewService.detail(id));
    }

    /**
     * 进房。
     *
     * @param id 面试 ID
     * @return 房间标识与房间页地址
     */
    @PostMapping("/{id}/join")
    @PreAuthorize("@permissionChecker.has('portal:interview:join')")
    public Result<PortalInterviewJoinView> join(@PathVariable long id) {
        return Result.ok(portalInterviewService.join(id));
    }
}
