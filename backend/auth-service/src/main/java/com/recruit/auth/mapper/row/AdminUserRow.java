package com.recruit.auth.mapper.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户管理分页/详情的数据库投影。
 *
 * <p>只带列表需要的列，避免把 {@code password_hash} 读到内存里。</p>
 */
@Data
public class AdminUserRow {

    private Long id;
    private String username;
    private String displayName;
    private String userType;
    private String status;
    private String phoneEnc;
    private Long deptId;
    private String deptName;
    private LocalDateTime createdAt;
    private Integer version;
}
