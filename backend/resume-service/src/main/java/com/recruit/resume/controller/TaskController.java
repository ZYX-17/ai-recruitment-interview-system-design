package com.recruit.resume.controller;

import com.recruit.common.context.UserContext;
import com.recruit.common.result.PageResult;
import com.recruit.common.result.Result;
import com.recruit.resume.dto.ParseTaskVO;
import com.recruit.resume.service.ResumeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 解析任务查询（前端任务中心/补偿页）。
 */
@Validated
@RestController
@RequestMapping("/api/resume/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ResumeService resumeService;

    @GetMapping
    public Result<PageResult<ParseTaskVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) long pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status) {
        UserContext.requireUserId();
        return Result.ok(resumeService.listTasks(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<ParseTaskVO> detail(@PathVariable Long id) {
        UserContext.requireUserId();
        return Result.ok(resumeService.getTask(id));
    }
}
