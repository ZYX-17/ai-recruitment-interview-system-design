package com.recruit.interview.dto;

import java.time.Instant;

/**
 * 邀请链接视图（内部接口/重新邀请共用）。
 *
 * @param url      邀请链接
 * @param expireAt 过期时间
 */
public record AsyncInviteLinkView(String url, Instant expireAt) {
}
