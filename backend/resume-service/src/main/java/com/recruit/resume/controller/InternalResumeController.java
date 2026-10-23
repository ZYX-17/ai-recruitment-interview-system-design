package com.recruit.resume.controller;

import com.recruit.common.result.Result;
import com.recruit.resume.dto.ParseCompletedRequest;
import com.recruit.resume.dto.ParseFailedRequest;
import com.recruit.resume.dto.ResumeParseStatusVO;
import com.recruit.resume.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 解析流水线服务间回调（X-Internal-Token 保护）。
 *
 * <p>mineru-worker 完成/失败后调用；resume-service 负责更新任务与简历状态，
 * 并在同一事务里写入 {@code resume.parse.completed/failed} Outbox 事件。</p>
 */
@RestController
@RequestMapping("/internal/resumes")
@RequiredArgsConstructor
public class InternalResumeController {

    private final ResumeService resumeService;

    @PostMapping("/{id}/parse-completed")
    public Result<ResumeParseStatusVO> parseCompleted(@PathVariable Long id,
                                                      @Valid @RequestBody ParseCompletedRequest request) {
        return Result.ok(resumeService.markParseCompleted(id, request));
    }

    @PostMapping("/{id}/parse-failed")
    public Result<ResumeParseStatusVO> parseFailed(@PathVariable Long id,
                                                   @Valid @RequestBody ParseFailedRequest request) {
        return Result.ok(resumeService.markParseFailed(id, request));
    }

    @GetMapping("/{id}/parse-status")
    public Result<ResumeParseStatusVO> parseStatus(@PathVariable Long id) {
        return Result.ok(resumeService.getParseStatus(id));
    }
}
