package com.recruit.resume.controller;

import com.recruit.common.context.UserContext;
import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.resume.dto.ResumeSearchHitVO;
import com.recruit.resume.dto.ResumeSearchRequest;
import com.recruit.resume.service.ResumeSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历检索接口（BM25 + kNN + RRF，ES 挂时 mysql-like）。
 */
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class SearchController {

    private final ResumeSearchService resumeSearchService;

    @PostMapping("/search")
    public Result<PageResult<ResumeSearchHitVO>> search(@Valid @RequestBody ResumeSearchRequest request) {
        UserContext.requireUserId();
        return Result.ok(resumeSearchService.search(request));
    }
}
