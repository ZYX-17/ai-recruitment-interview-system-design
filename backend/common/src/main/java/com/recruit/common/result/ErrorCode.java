package com.recruit.common.result;

import lombok.Getter;

/**
 * 错误码全集（与开发文档 3.3 对齐：AUTH1/RSM2/ITV3/ROM4/AIS5/NTF6/FIL7/GW9 号段）。
 *
 * <p><b>号段规则：</b>号段前缀表示归属模块，方便前端按模块做统一提示，也方便日志按号段统计：</p>
 * <ul>
 *   <li>{@code AUTH1xxx}：认证、令牌、权限；</li>
 *   <li>{@code RSM2xxx}：简历、候选人、人才库；</li>
 *   <li>{@code ITV3xxx}：投递、排期、面试；</li>
 *   <li>{@code ROM4xxx}：面试房间；</li>
 *   <li>{@code AIS5xxx}：AI 能力；</li>
 *   <li>{@code NTF6xxx}：通知；</li>
 *   <li>{@code FIL7xxx}：文件；</li>
 *   <li>{@code GW9xxx}：网关与公共能力。</li>
 * </ul>
 *
 * <p><b>三个字段的分工：</b></p>
 * <ul>
 *   <li>{@code code}：业务码，放在响应体里，前端用它判断具体错误（如 ITV3101 排期冲突）；</li>
 *   <li>{@code defaultMessage}：默认展示文案，调用方不传自定义文案时使用；</li>
 *   <li>{@code httpStatus}：HTTP 状态码，让网关、监控、浏览器能正确识别成功/失败类型。</li>
 * </ul>
 *
 * <p><b>为什么 HTTP 状态码要放在错误码上：</b>如果让每个 Service 自己决定返回 400 还是 409，
 * 同一种错误在不同接口会出现不同状态码。集中定义后，{@code GlobalExceptionHandler}
 * 只读 {@link #getHttpStatus()}，行为保持一致。</p>
 *
 * <p>新增错误码的流程：先在本文件登记，再同步到开发文档 3.3 的错误码表，最后补单元测试。
 * 不要复用已有码表达新语义，否则前端和历史日志无法区分。</p>
 */
@Getter
public enum ErrorCode {

    SUCCESS("0", "成功", 200),
    SYS_ERROR("SYS500", "系统繁忙，请稍后再试", 500),
    PARAM_INVALID("SYS400", "参数校验失败", 400),
    /** 同一个 Idempotency-Key 的首次请求仍在执行，客户端应稍后重试。 */
    IDEMPOTENT_IN_PROGRESS("SYS409", "请求正在处理中，请勿重复提交", 409),

    // AUTH1xxx 认证/令牌
    AUTH_LOGIN_FAILED("AUTH1001", "用户名或密码错误", 401),
    AUTH_REFRESH_INVALID("AUTH1002", "Refresh Token 无效或已过期", 401),
    AUTH_REFRESH_REPLAYED("AUTH1003", "令牌重放，请重新登录", 401),
    AUTH_FORBIDDEN("AUTH1101", "权限不足", 403),

    // RSM2xxx 简历/人才
    RESUME_NOT_FOUND("RSM2001", "简历不存在", 404),
    PERSON_NOT_FOUND("RSM2002", "候选人不存在", 404),
    PERSON_DUPLICATED("RSM2003", "候选人已存在或重复投递", 409),
    RESUME_VERSION_CONFLICT("RSM2004", "简历已被修改，请刷新后重试", 409),
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

    /** 业务错误码，返回在响应体的 {@code code} 字段；成功固定为 "0"。 */
    private final String code;

    /** 默认展示文案，要求是用户能看懂的短句，不包含技术细节。 */
    private final String defaultMessage;

    /**
     * HTTP 状态码。由 GlobalExceptionHandler 读取并写到响应状态行；
     * 业务码仍放在响应 body 的 {@code code} 字段，两者互不替代。
     */
    private final int httpStatus;

    /**
     * 兼容旧写法的两参构造：未显式指定 HTTP 状态码时按 500 处理。
     *
     * <p>保留它是为了不破坏已有枚举常量的声明；新错误码建议直接用三参构造，
     * 明确写出语义状态码（404/409/429 等）。</p>
     *
     * @param code           业务码
     * @param defaultMessage 默认文案
     */
    ErrorCode(String code, String defaultMessage) {
        this(code, defaultMessage, 500);
    }

    /**
     * 完整构造。
     *
     * @param code           业务码，全局唯一，建议带模块前缀
     * @param defaultMessage 默认展示文案
     * @param httpStatus     HTTP 状态码，例如 400/401/403/404/409/429/500/503
     */
    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }
}
