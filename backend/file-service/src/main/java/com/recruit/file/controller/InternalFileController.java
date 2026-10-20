package com.recruit.file.controller;

import com.recruit.common.result.Result;
import com.recruit.file.dto.DownloadUrlResponse;
import com.recruit.file.dto.FileMetaVO;
import com.recruit.file.dto.InternalCompleteRequest;
import com.recruit.file.dto.InternalPresignDownloadRequest;
import com.recruit.file.dto.InternalPresignUploadRequest;
import com.recruit.file.dto.InternalRegisterRequest;
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
 * 文件服务间接口（{@code /internal/**}，网关对外屏蔽 + X-Internal-Token）。
 *
 * <p>resume-service / ai-service / mineru-worker 都通过这组接口访问对象存储，
 * 避免每个服务各自持有 MinIO 密钥（见 docs/b/03-services.md §对象存储边界）。</p>
 */
@RestController
@RequestMapping("/internal/files")
@RequiredArgsConstructor
public class InternalFileController {

    private final FileService fileService;

    @PostMapping("/register")
    public Result<FileMetaVO> register(@Valid @RequestBody InternalRegisterRequest request) {
        return Result.ok(fileService.register(request));
    }

    @PostMapping("/presign/upload")
    public Result<PresignUploadResponse> presignUpload(@Valid @RequestBody InternalPresignUploadRequest request) {
        return Result.ok(fileService.presignUpload(request.toPresignRequest(), request.uploaderId()));
    }

    @PostMapping("/presign/download")
    public Result<DownloadUrlResponse> presignDownload(@Valid @RequestBody InternalPresignDownloadRequest request) {
        return Result.ok(fileService.downloadUrl(request.fileId()));
    }

    @GetMapping("/{id}/meta")
    public Result<FileMetaVO> meta(@PathVariable Long id) {
        return Result.ok(fileService.getMeta(id));
    }

    @PostMapping("/{id}/complete")
    public Result<FileMetaVO> complete(@PathVariable Long id,
                                       @Valid @RequestBody InternalCompleteRequest request) {
        return Result.ok(fileService.completeInternal(id, request.sha256()));
    }
}
