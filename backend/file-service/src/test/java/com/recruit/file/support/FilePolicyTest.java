package com.recruit.file.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 文件策略单测：MIME 白名单、大小上限、扩展名推导。
 */
class FilePolicyTest {

    @Test
    void acceptsResumePdfWithCharsetParameter() {
        assertDoesNotThrow(() -> FilePolicy.validate("RESUME", "application/pdf; charset=binary", 1024));
    }

    @Test
    void rejectsOversizedResume() {
        BizException ex = assertThrows(BizException.class,
                () -> FilePolicy.validate("RESUME", "application/pdf", 21L * 1024 * 1024));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
    }

    @Test
    void rejectsUnsupportedMime() {
        BizException ex = assertThrows(BizException.class,
                () -> FilePolicy.validate("RESUME", "video/mp4", 1024));
        assertEquals(ErrorCode.FILE_MIME_UNSUPPORTED, ex.getErrorCode());
    }

    @Test
    void rejectsUnknownBizType() {
        BizException ex = assertThrows(BizException.class,
                () -> FilePolicy.validate("UNKNOWN", "application/pdf", 1024));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void derivesExtensionFromFileNameFirst() {
        assertEquals("pdf", FilePolicy.extensionOf("张三-简历.PDF", "application/pdf"));
        assertEquals("jpg", FilePolicy.extensionOf("photo.jpg", "image/png"));
    }

    @Test
    void derivesExtensionFromMimeWhenFileNameHasNoSuffix() {
        assertEquals("docx", FilePolicy.extensionOf("resume",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals("bin", FilePolicy.extensionOf("blob", "application/octet-stream"));
    }

    @Test
    void acceptsMarkdownParseArtifact() {
        assertDoesNotThrow(() -> FilePolicy.validate("PARSE_ARTIFACT", "text/markdown", 1024));
        assertEquals("md", FilePolicy.extensionOf("resume-1.md", "text/markdown"));
        assertEquals("md", FilePolicy.extensionOf("without-suffix", "text/markdown"));
    }
}
