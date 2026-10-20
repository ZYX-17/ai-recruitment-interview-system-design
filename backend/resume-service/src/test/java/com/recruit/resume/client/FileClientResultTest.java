package com.recruit.resume.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.result.Result;
import com.recruit.resume.client.dto.FileMetaDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Feign 解码链路：file-service 返回的 Result 包装能被 Jackson 还原。
 * 这条链路依赖 Result 的无参构造与 FileMetaDTO record 的字段名。
 */
class FileClientResultTest {

    @Test
    void deserializesWrappedFileMeta() throws Exception {
        String json = """
                {
                  "code": "0",
                  "message": "成功",
                  "data": {
                    "id": "100",
                    "fileKey": "key-1",
                    "fileName": "张三-简历.pdf",
                    "mime": "application/pdf",
                    "size": 123,
                    "bizType": "RESUME",
                    "bizId": null,
                    "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "status": "OK",
                    "createdAt": "2026-10-20T00:00:00Z",
                    "updatedAt": "2026-10-20T00:00:00Z"
                  },
                  "traceId": "trace-1",
                  "timestamp": 1
                }
                """;
        Result<FileMetaDTO> result = new ObjectMapper()
                .readValue(json, new TypeReference<Result<FileMetaDTO>>() {
                });

        assertTrue(result.isSuccess());
        assertEquals("key-1", result.getData().fileKey());
        assertEquals("RESUME", result.getData().bizType());
        assertEquals(123, result.getData().size());
    }
}
