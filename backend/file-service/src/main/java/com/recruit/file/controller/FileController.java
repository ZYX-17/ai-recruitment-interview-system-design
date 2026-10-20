package com.recruit.file.controller;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.Result;
import com.recruit.file.dto.CompleteUploadRequest;
import com.recruit.file.dto.DownloadUrlResponse;
import com.recruit.file.dto.FileMetaVO;
import com.recruit.file.dto.PresignUploadRequest;
import com.recruit.file.dto.PresignUploadResponse;
import com.recruit.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件对外接口（网关后，需登录）。
 *
 * <p>网关校验 JWT 后注入 {@code X-User-Uid}；这里只负责业务，不解析令牌。
 * 对象级权限（某份简历能否下载）由 resume-service 判断，本服务保证“登录 + 状态合法”。</p>
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/presign/upload")
    public Result<PresignUploadResponse> presignUpload(@Valid @RequestBody PresignUploadRequest request) {
        return Result.ok(fileService.presignUpload(request, currentUserId()));
    }

    @PostMapping("/complete")
    public Result<FileMetaVO> complete(@Valid @RequestBody CompleteUploadRequest request) {
        return Result.ok(fileService.complete(request, currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<FileMetaVO> meta(@PathVariable Long id) {
        // 要求登录：未认证请求在这里直接 401
        UserContext.requireUserId();
        return Result.ok(fileService.getMeta(id));
    }

    @GetMapping("/{id}/url")
    public Result<DownloadUrlResponse> downloadUrl(@PathVariable Long id) {
        UserContext.requireUserId();
        return Result.ok(fileService.downloadUrl(id));
    }

    private long currentUserId() {
        String uid = UserContext.requireUserId();
        try {
            return Long.parseLong(uid);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户标识非法");
        }
    }
}
