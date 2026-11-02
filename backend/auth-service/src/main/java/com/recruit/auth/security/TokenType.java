package com.recruit.auth.security;

/**
 * 令牌类型。Access 只能访问业务接口，Refresh 只能换发新令牌。
 *
 * <p>类型写进 JWT 的 {@code typ} claim，校验时强匹配：用 Refresh 直接访问业务接口必须失败。</p>
 */
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 解析 claim 中的类型值。
     *
     * @param value claim 值
     * @return 对应类型
     * @throws IllegalArgumentException 值缺失或未知
     */
    public static TokenType from(String value) {
        if (value != null) {
            for (TokenType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("未知的令牌类型: " + value);
    }
}
