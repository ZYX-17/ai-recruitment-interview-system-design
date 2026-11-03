package com.recruit.auth.dto;

/**
 * 候选人注册结果（A-02）。
 *
 * @param userId      新用户 ID
 * @param username    登录名
 * @param displayName 显示姓名
 * @param userType    固定为 CANDIDATE
 */
public record RegisterResult(String userId, String username, String displayName, String userType) {
}
