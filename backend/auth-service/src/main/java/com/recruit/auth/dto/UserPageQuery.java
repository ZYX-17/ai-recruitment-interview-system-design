package com.recruit.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户分页查询入参（A-07）。
 *
 * <p>{@code pageSize} 上限 100，避免一次拉全表；{@code keyword} 同时匹配登录名与显示名。</p>
 */
@Data
public class UserPageQuery {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "pageNum 必须从 1 开始")
    private long pageNum = 1;

    /** 每页条数，最大 100。 */
    @Min(value = 1, message = "pageSize 必须大于 0")
    @Max(value = 100, message = "pageSize 不能超过 100")
    private long pageSize = 20;

    /** 关键字：登录名或显示名包含匹配。 */
    @Size(max = 64, message = "keyword 长度不能超过 64")
    private String keyword;

    /** 状态过滤：ACTIVE / LOCKED / DISABLED。 */
    @Pattern(regexp = "ACTIVE|LOCKED|DISABLED", message = "status 只能是 ACTIVE/LOCKED/DISABLED")
    private String status;

    /** 部门过滤。 */
    private Long deptId;
}
