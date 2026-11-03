package com.recruit.interview.controller;

import com.recruit.common.result.Result;
import com.recruit.interview.dto.AsyncAiResultForm;
import com.recruit.interview.dto.AsyncAnswerView;
import com.recruit.interview.dto.AsyncInviteLinkView;
import com.recruit.interview.service.AsyncInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 异步面试内部接口（网关对公网 403，需 {@code X-Internal-Token}）。
 */
@RestController
@RequestMapping("/internal/async-interviews")
@RequiredArgsConstructor
public class AsyncInternalController {

    private final AsyncInterviewService service;

    /**
     * ai-service 回写逐题转写/评分。
     *
     * @param answerId 作答 ID
     * @param form     回写入参
     * @return 回写后的作答
     */
    @PostMapping("/answers/{answerId}/ai-result")
    public Result<AsyncAnswerView> aiResult(@PathVariable long answerId,
                                            @Valid @RequestBody AsyncAiResultForm form) {
        return Result.ok(service.applyAiResult(answerId, form));
    }

    /**
     * notify 现取邀请链接（缓存 10 分钟，不轮换 token）。
     *
     * @param id 试卷 ID
     * @return 链接
     */
    @PostMapping("/{id}/invite-link")
    public Result<AsyncInviteLinkView> inviteLink(@PathVariable long id) {
        return Result.ok(service.cachedInviteLink(id));
    }
}
