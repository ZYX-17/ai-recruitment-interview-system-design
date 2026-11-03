package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.MinutesView;
import com.recruit.interview.dto.MinutesWriteForm;
import com.recruit.interview.service.MinutesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面试内部接口（ai-service 专用，网关对公网 403，需 {@code X-Internal-Token}）。
 */
@RestController
@RequestMapping("/internal/interviews")
@RequiredArgsConstructor
public class InterviewInternalController {

    private final MinutesService minutesService;

    /**
     * 回写 AI 纪要/评价草稿（按 taskId 幂等）。
     *
     * @param id   面试 ID
     * @param form 回写入参
     * @return 纪要
     */
    @PostMapping("/{id}/minutes")
    public Result<MinutesView> writeMinutes(@PathVariable long id,
                                            @Valid @RequestBody MinutesWriteForm form) {
        return Result.ok(minutesService.write(id, form));
    }
}
