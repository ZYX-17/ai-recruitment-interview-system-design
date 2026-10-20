package com.recruit.file.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文件业务策略：按 biz_type 限定 MIME 白名单与大小上限。
 *
 * <p>为什么集中在这里：预签名、完成上传、内部登记三个入口都必须用同一套规则，
 * 分散写容易出现“预签名放行、完成时又拒绝”的不一致。错误码固定为
 * FIL7002（过大）/FIL7003（类型不支持），前端可按码提示。</p>
 */
public final class FilePolicy {

    private static final long MB = 1024L * 1024L;

    /**
     * 单个业务类型的规则。
     *
     * @param mimes    允许的 MIME 集合（已规范化）
     * @param maxBytes 大小上限（字节）
     */
    public record Rule(Set<String> mimes, long maxBytes) {
    }

    private static final Map<String, Rule> RULES = Map.of(
            "RESUME", new Rule(Set.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "image/jpeg",
                    "image/png"), 20 * MB),
            "RECORDING", new Rule(Set.of(
                    "video/mp4",
                    "video/webm",
                    "video/quicktime",
                    "audio/mpeg",
                    "audio/wav"), 500 * MB),
            "ANSWER_VIDEO", new Rule(Set.of(
                    "video/mp4",
                    "video/webm",
                    "video/quicktime"), 500 * MB),
            "EVIDENCE", new Rule(Set.of(
                    "application/pdf",
                    "image/jpeg",
                    "image/png"), 20 * MB),
            "AVATAR", new Rule(Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"), 5 * MB),
            "ATTACHMENT", new Rule(Set.of(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/zip"), 50 * MB)
    );

    /** MIME → 默认扩展名（文件名没有可用扩展名时兜底）。 */
    private static final Map<String, String> EXTENSION_BY_MIME = Map.ofEntries(
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/zip", "zip"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"),
            Map.entry("video/quicktime", "mov"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/wav", "wav")
    );

    private FilePolicy() {
    }

    /**
     * 取业务类型规则；未知类型按参数错误处理，避免调用方传错 biz_type 后落到宽泛默认值。
     */
    public static Rule ruleOf(String bizType) {
        Rule rule = bizType == null ? null : RULES.get(bizType.trim().toUpperCase(Locale.ROOT));
        if (rule == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的文件业务类型：" + bizType);
        }
        return rule;
    }

    /**
     * 规范化 MIME：转小写、去掉 {@code ;charset=...} 等参数。
     */
    public static String normalizeMime(String mime) {
        if (mime == null) {
            return "";
        }
        int semicolon = mime.indexOf(';');
        String value = semicolon >= 0 ? mime.substring(0, semicolon) : mime;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 校验业务类型 + MIME + 大小；不通过直接抛业务异常。
     */
    public static void validate(String bizType, String mime, long size) {
        Rule rule = ruleOf(bizType);
        String normalized = normalizeMime(mime);
        if (!rule.mimes().contains(normalized)) {
            throw new BizException(ErrorCode.FILE_MIME_UNSUPPORTED,
                    "不支持的文件类型：" + (normalized.isBlank() ? "unknown" : normalized));
        }
        if (size > rule.maxBytes()) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE,
                    "文件过大，上限 " + (rule.maxBytes() / MB) + "MB");
        }
    }

    /**
     * 推导对象扩展名：优先用文件名里的合法扩展名，否则按 MIME 映射，最后兜底 bin。
     */
    public static String extensionOf(String fileName, String mime) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            String extension = lower.substring(dot + 1);
            if (extension.matches("[a-z0-9]{1,8}")) {
                return extension;
            }
        }
        return EXTENSION_BY_MIME.getOrDefault(normalizeMime(mime), "bin");
    }
}
