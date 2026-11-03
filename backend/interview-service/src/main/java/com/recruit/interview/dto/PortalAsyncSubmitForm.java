package com.recruit.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 候选人提交异步面试入参（{@code POST /portal/async-invites/{token}/submit}）。
 */
@Data
public class PortalAsyncSubmitForm {

    @NotEmpty
    @Valid
    private List<PortalAsyncAnswerForm> answers;
}
