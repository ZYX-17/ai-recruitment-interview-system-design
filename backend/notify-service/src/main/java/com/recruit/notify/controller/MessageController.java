package com.recruit.notify.controller;

import com.recruit.common.result.Result;
import com.recruit.notify.dto.MessagePageQuery;
import com.recruit.notify.dto.MessagePageView;
import com.recruit.notify.dto.MessageView;
import com.recruit.notify.service.MessageService;
import com.recruit.notify.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内信端点（OpenAPI §15 N-01/N-02/X-08）。
 */
@RestController
@RequestMapping("/api/notify/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final NotificationService notificationService;

    /**
     * 我的通知分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    public Result<MessagePageView> page(@Valid MessagePageQuery query) {
        return Result.ok(messageService.page(query));
    }

    /**
     * 标记已读（仅本人，幂等）。
     *
     * @param id 通知 ID
     * @return 空结果
     */
    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    /**
     * 重发失败通知（管理员）。
     *
     * @param id 通知 ID
     * @return 当前通知
     */
    @PostMapping("/{id}/resend")
    @PreAuthorize("@permissionChecker.has('system:notice:update')")
    public Result<MessageView> resend(@PathVariable long id) {
        return Result.ok(notificationService.resend(id));
    }
}
