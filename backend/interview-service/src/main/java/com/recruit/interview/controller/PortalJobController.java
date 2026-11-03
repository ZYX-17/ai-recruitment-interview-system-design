package com.recruit.interview.controller;

import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.interview.dto.PortalJobPageQuery;
import com.recruit.interview.dto.PortalJobView;
import com.recruit.interview.service.PortalJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 门户岗位浏览（I-10，公开只读）。
 *
 * <p>安全链白名单 {@code /api/interview/portal/**}；只返回已发布岗位。</p>
 */
@RestController
@RequestMapping("/api/interview/portal/jobs")
@RequiredArgsConstructor
public class PortalJobController {

    private final PortalJobService portalJobService;

    /**
     * 已发布岗位分页。
     *
     * @param query 分页与关键词
     * @return 分页岗位
     */
    @GetMapping
    public Result<PageResult<PortalJobView>> page(@Valid PortalJobPageQuery query) {
        return Result.ok(portalJobService.page(query));
    }

    /**
     * 已发布岗位详情。
     *
     * @param id 岗位 ID
     * @return 岗位详情
     */
    @GetMapping("/{id}")
    public Result<PortalJobView> detail(@PathVariable long id) {
        return Result.ok(portalJobService.detail(id));
    }
}
