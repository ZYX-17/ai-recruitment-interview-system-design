package com.recruit.auth.support;

import com.recruit.auth.security.UserPrincipal;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;

/**
 * 从登录主体解析操作者 ID。
 *
 * <p>管理员接口统一走这里，避免每个 Controller 重复写一遍解析与异常兜底。</p>
 */
public final class PrincipalHelper {

    private PrincipalHelper() {
    }

    /**
     * 解析当前登录用户 ID。
     *
     * @param principal 登录主体，未认证时为 null
     * @return 用户 ID
     */
    public static long operatorId(UserPrincipal principal) {
        if (principal == null) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED);
        }
        try {
            return Long.parseLong(principal.getUserId());
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED);
        }
    }
}
