package com.recruit.auth.mapper.row;

import lombok.Data;

/**
 * 权限校验用的最小投影：只带 ID、类型与父节点。
 */
@Data
public class PermissionBriefRow {

    private Long id;
    private String type;
    private Long parentId;
}
