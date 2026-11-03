package com.recruit.auth.mapper.row;

import lombok.Data;

/**
 * 用户-角色联查投影。
 */
@Data
public class UserRoleRow {

    private Long userId;
    private Long roleId;
    private String roleCode;
    private String roleName;
}
