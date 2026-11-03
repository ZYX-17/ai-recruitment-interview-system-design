package com.recruit.auth.mapper.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色分页/详情的数据库投影，附带绑定用户数。
 */
@Data
public class AdminRoleRow {

    private Long id;
    private String roleCode;
    private String roleName;
    private String dataScope;
    private Boolean isSystemRole;
    private String status;
    private String remark;
    private Long userCount;
    private LocalDateTime createdAt;
    private Integer version;
}
