package com.recruit.auth.dto;

/**
 * 用户详情/列表里的角色摘要。
 *
 * @param id   角色 ID（字符串输出，避免前端 JS 精度丢失）
 * @param code 角色码
 * @param name 角色名
 */
public record RoleBrief(String id, String code, String name) {
}
