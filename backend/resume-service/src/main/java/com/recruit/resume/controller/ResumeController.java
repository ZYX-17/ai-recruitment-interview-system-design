package com.recruit.resume.controller;

import com.recruit.common.context.UserContext;
import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.resume.dto.CreateResumeRequest;
import com.recruit.resume.dto.CreateResumeResponse;
import com.recruit.resume.dto.ParseTaskVO;
import com.recruit.resume.dto.ResumeMarkdownVO;
import com.recruit.resume.dto.ResumeParseStatusVO;
import com.recruit.resume.dto.ResumeVO;
import com.recruit.resume.dto.UpdateStructuredRequest;
import com.recruit.resume.service.ResumeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简历接口（P2 主线）。权限码由网关/权限中心校验，本服务只信任网关注入的用户头。
 */
@Validated
@RestController
@RequestMapping("/api/resume/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping
    public Result<CreateResumeResponse> create(@Valid @RequestBody CreateResumeRequest request) {
        UserContext.requireUserId();
        return Result.ok(resumeService.create(request));
    }

    @GetMapping
    public Result<PageResult<ResumeVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String parseStatus,
            @RequestParam(required = false) Long personId) {
        UserContext.requireUserId();
        return Result.ok(resumeService.listResumes(pageNum, pageSize, keyword, parseStatus, personId));
    }

    @GetMapping("/{id}")
    public Result<ResumeVO> detail(@PathVariable Long id) {
        UserContext.requireUserId();
        return Result.ok(resumeService.getResume(id));
    }

    @GetMapping("/{id}/parse-status")
    public Result<ResumeParseStatusVO> parseStatus(@PathVariable Long id) {
        UserContext.requireUserId();
        return Result.ok(resumeService.getParseStatus(id));
    }

    @GetMapping("/{id}/markdown")
    public Result<ResumeMarkdownVO> markdown(@PathVariable Long id) {
        UserContext.requireUserId();
        return Result.ok(resumeService.getMarkdownUrl(id));
    }

    @PutMapping("/{id}/structured")
    public Result<ResumeVO> updateStructured(@PathVariable Long id,
                                             @Valid @RequestBody UpdateStructuredRequest request) {
        UserContext.requireUserId();
        return Result.ok(resumeService.updateStructured(id, request));
    }

    @PostMapping("/{id}/retry-parse")
    public Result<ParseTaskVO> retryParse(@PathVariable Long id) {
        UserContext.requireUserId();
        return Result.ok(resumeService.retryParse(id));
    }
}
