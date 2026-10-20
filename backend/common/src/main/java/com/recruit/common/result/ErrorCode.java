package com.recruit.common.result;

import lombok.Getter;

/**
 * 错误码全集（与开发文档 3.3 对齐：AUTH1/RSM2/ITV3/ROM4/AIS5/NTF6/FIL7/GW9 号段）。
 * 新增错误码先登记文档再写代码。
 */
@Getter
public enum ErrorCode {

    SUCCESS("0", "成功", 200),
    SYS_ERROR("SYS500", "系统繁忙，请稍后再试", 500),
    PARAM_INVALID("SYS400", "参数校验失败", 400),

    // AUTH1xxx 认证/令牌
    AUTH_LOGIN_FAILED("AUTH1001", "用户名或密码错误", 401),
    AUTH_REFRESH_INVALID("AUTH1002", "Refresh Token 无效或已过期", 401),
    AUTH_REFRESH_REPLAYED("AUTH1003", "令牌重放，请重新登录", 401),
    AUTH_FORBIDDEN("AUTH1101", "权限不足", 403),

    // RSM2xxx 简历/人才
    RESUME_NOT_FOUND("RSM2001", "简历不存在", 404),
    PERSON_NOT_FOUND("RSM2002", "候选人不存在", 404),
    PERSON_DUPLICATED("RSM2003", "候选人已存在或重复投递", 409),
    RESUME_PARSE_FAILED("RSM2101", "简历解析失败", 422),
    PARSE_TASK_NOT_FOUND("RSM2102", "解析任务不存在", 404),
    PARSE_RETRY_EXCEEDED("RSM2103", "解析重试次数超限", 429),
    RESUME_MERGE_ILLEGAL("RSM2201", "合并目标非法", 409),
    MERGE_RECORD_NOT_FOUND("RSM2202", "合并记录不存在", 404),
    SCREENING_RULE_INVALID("RSM2301", "筛选规则非法", 400),
    MATCH_RESULT_NOT_FOUND("RSM2302", "匹配结果不存在", 404),
    TALENT_NOT_FOUND("RSM2401", "人才档案不存在", 404),
    SEARCH_ENGINE_UNAVAILABLE("RSM2501", "检索服务不可用，已降级", 503),

    // ITV3xxx 面试
    APP_NOT_FOUND("ITV3001", "投递不存在", 404),
    SCHEDULE_CONFLICT("ITV3101", "排期冲突：候选人/面试官/资源时间段已被占用", 409),
    ILLEGAL_TRANSITION("ITV3102", "状态流转非法", 409),

    // ROM4xxx 房间
    ROOM_NOT_FOUND("ROM4001", "房间不存在", 404),
    ROOM_NOT_OPEN("ROM4003", "未到进场时间", 403),
    ROOM_FULL("ROM4004", "房间名额已满", 409),
    NOT_IN_ROSTER("ROM4005", "不在受邀名单", 403),

    // AIS5xxx AI
    AI_TIMEOUT("AIS5001", "模型调用超时", 504),
    AI_DEGRADED("AIS5002", "AI 已降级，请稍后重试", 503),
    AI_TASK_NOT_FOUND("AIS5003", "AI 任务不存在", 404),
    AI_SCHEMA_INVALID("AIS5004", "AI 输出 Schema 校验失败", 502),
    AI_PROMPT_NOT_FOUND("AIS5005", "Prompt 版本不存在", 404),
    AI_QUOTA_EXCEEDED("AIS5006", "AI 配额或预算超限", 429),
    ASR_OVERLOAD("AIS5101", "ASR 并发已满，实时字幕暂停", 503),
    ANSWER_SUBMITTED("AIS5102", "作答已提交，不可重复提交", 409),
    EMBEDDING_FAILED("AIS5103", "Embedding 失败", 502),

    // FIL7xxx 文件
    FILE_NOT_FOUND("FIL7001", "文件不存在", 404),
    FILE_TOO_LARGE("FIL7002", "文件过大", 413),
    FILE_MIME_UNSUPPORTED("FIL7003", "文件类型不支持", 415),
    FILE_UPLOAD_INCOMPLETE("FIL7004", "上传未完成", 409),
    FILE_PRESIGN_FAILED("FIL7005", "预签名失败", 502),
    FILE_STORAGE_ERROR("FIL7006", "对象存储异常", 503),
    FILE_CHECKSUM_MISMATCH("FIL7007", "校验和不匹配", 400),
    FILE_DOWNLOAD_FORBIDDEN("FIL7008", "无权下载", 403),

    // GW9xxx 网关
    GW_UNAUTHENTICATED("GW9001", "未认证", 401),
    GW_RATE_LIMITED("GW9003", "请求过于频繁", 429),
    GW_INTERNAL_FORBIDDEN("GW9005", "内部接口禁止外部访问", 403);

    private final String code;
    private final String defaultMessage;
    /** HTTP 状态码；由 GlobalExceptionHandler 使用，业务码仍放在响应 body。 */
    private final int httpStatus;

    ErrorCode(String code, String defaultMessage) {
        this(code, defaultMessage, 500);
    }

    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
