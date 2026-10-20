package com.recruit.resume.client;

import com.recruit.common.result.Result;
import com.recruit.resume.client.dto.DownloadUrlDTO;
import com.recruit.resume.client.dto.FileMetaDTO;
import com.recruit.resume.client.dto.InternalPresignDownloadRequest;
import com.recruit.resume.config.InternalFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * file-service 内部接口客户端。
 *
 * <p>resume-service 不直接持有 MinIO 凭据，所有对象存储操作都经 file-service；
 * 调用自动带 X-Internal-Token / X-Trace-Id（见 {@link InternalFeignConfig}）。</p>
 */
@FeignClient(name = "file-service", url = "${recruit.file-service.url:}",
        path = "/internal/files", configuration = InternalFeignConfig.class)
public interface FileClient {

    @GetMapping("/{id}/meta")
    Result<FileMetaDTO> meta(@PathVariable("id") Long id);

    @PostMapping("/presign/download")
    Result<DownloadUrlDTO> presignDownload(@RequestBody InternalPresignDownloadRequest request);
}
