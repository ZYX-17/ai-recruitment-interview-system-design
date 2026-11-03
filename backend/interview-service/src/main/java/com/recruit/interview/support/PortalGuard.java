package com.recruit.interview.support;

import com.recruit.common.context.UserContext;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;

/**
 * 门户身份守卫：候选人接口只接受 CANDIDATE 角色。
 *
 * <p>门户在安全链上放行（岗位公开、Offer/异步邀请 token 自助），
 * 因此账号型门户接口必须自行校验当前用户与角色，不能信任请求参数。</p>
 */
public final class PortalGuard {

    /** 候选人角色码。 */
    public static final String ROLE_CANDIDATE = "CANDIDATE";

    private PortalGuard() {
    }

    /**
     * 取当前候选人 ID。
     *
     * @return 当前用户 ID
     * @throws BizException 未登录 401，非候选人 403
     */
    public static long requireCandidateId() {
        String userId = UserContext.requireUserId();
        if (!UserContext.hasRole(ROLE_CANDIDATE)) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "仅候选人可访问");
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.GW_UNAUTHENTICATED, "当前用户标识非法");
        }
    }
}
