package com.recruit.common.result;

import lombok.Getter;

/**
 * 错误码全集（与开发文档 3.3 对齐：AUTH1/RSM2/ITV3/ROM4/AIS5/NTF6/FIL7/GW9 号段）。
 * 新增错误码先登记文档再写代码。
 */
@Getter
public enum ErrorCode {

    SUCCESS("0", "成功"),
    SYS_ERROR("SYS500", "系统繁忙，请稍后再试"),
    PARAM_INVALID("SYS400", "参数校验失败"),

    // AUTH1xxx 认证/令牌
    AUTH_LOGIN_FAILED("AUTH1001", "用户名或密码错误"),
    AUTH_REFRESH_INVALID("AUTH1002", "Refresh Token 无效或已过期"),
    AUTH_REFRESH_REPLAYED("AUTH1003", "令牌重放，请重新登录"),
    AUTH_FORBIDDEN("AUTH1101", "权限不足"),

    // RSM2xxx 简历
    RESUME_NOT_FOUND("RSM2001", "简历不存在"),
    RESUME_PARSE_FAILED("RSM2101", "简历解析失败"),
    RESUME_MERGE_ILLEGAL("RSM2201", "合并目标非法"),

    // ITV3xxx 面试
    APP_NOT_FOUND("ITV3001", "投递不存在"),
    SCHEDULE_CONFLICT("ITV3101", "排期冲突：候选人/面试官/资源时间段已被占用"),
    ILLEGAL_TRANSITION("ITV3102", "状态流转非法"),

    // ROM4xxx 房间
    ROOM_NOT_FOUND("ROM4001", "房间不存在"),
    ROOM_NOT_OPEN("ROM4003", "未到进场时间"),
    ROOM_FULL("ROM4004", "房间名额已满"),
    NOT_IN_ROSTER("ROM4005", "不在受邀名单"),

    // AIS5xxx AI
    AI_TIMEOUT("AIS5001", "模型调用超时"),
    AI_DEGRADED("AIS5002", "AI 已降级，请稍后重试"),
    ANSWER_SUBMITTED("AIS5102", "作答已提交，不可重复提交"),

    // GW9xxx 网关
    GW_UNAUTHENTICATED("GW9001", "未认证"),
    GW_RATE_LIMITED("GW9003", "请求过于频繁"),
    GW_INTERNAL_FORBIDDEN("GW9005", "内部接口禁止外部访问");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
