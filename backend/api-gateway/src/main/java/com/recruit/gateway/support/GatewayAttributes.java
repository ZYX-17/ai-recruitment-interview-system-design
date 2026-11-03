package com.recruit.gateway.support;

/**
 * 网关过滤器之间传递数据的 exchange attribute 名称。
 */
public final class GatewayAttributes {

    /** 已验签用户快照，{@link com.recruit.gateway.security.GatewayUser}。 */
    public static final String USER = GatewayAttributes.class.getName() + ".user";

    private GatewayAttributes() {
    }
}
