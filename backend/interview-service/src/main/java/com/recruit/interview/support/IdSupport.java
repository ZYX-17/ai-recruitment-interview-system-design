package com.recruit.interview.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;

/**
 * 字符串 ID 与 long 的转换。
 *
 * <p>接口层 ID 统一用字符串（前端 JS 安全整数限制），落库前集中解析；
 * 解析失败返回 400，不把 {@link NumberFormatException} 冒成 500。</p>
 */
public final class IdSupport {

    private IdSupport() {
    }

    /**
     * 解析必填 ID。
     *
     * @param value 字符串 ID
     * @param field 字段名，用于错误文案
     * @return long 值
     */
    public static long requireLong(String value, String field) {
        Long parsed = optionalLong(value, field);
        if (parsed == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + "不能为空");
        }
        return parsed;
    }

    /**
     * 解析可选 ID。
     *
     * @param value 字符串 ID，可为空
     * @param field 字段名，用于错误文案
     * @return long 值；空字符串返回 null
     */
    public static Long optionalLong(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + "格式非法");
        }
    }

    /**
     * 输出 ID 字符串。
     *
     * @param value 数值 ID
     * @return 字符串；null 返回 null
     */
    public static String asString(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
