package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改部门入参（PUT 全量语义）。
 *
 * <p>{@code parentId} 传 null 表示移动到根级；移动时服务端会重写整棵子树的
 * {@code ancestors}，并校验层级上限与列宽。{@code deptName} 为空表示不修改。</p>
 */
@Data
public class UpdateDeptForm {

    /** 部门名称；为空表示不修改。 */
    @Size(max = 64, message = "部门名称长度不能超过 64")
    private String deptName;

    /** 父部门 ID；null 表示根级。 */
    private Long parentId;

    /** 排序；为空表示不修改。 */
    @Min(value = 0, message = "sort 不能为负数")
    private Integer sort;

    /** 状态；为空表示不修改。 */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能是 ACTIVE/DISABLED")
    private String status;

    /** 乐观锁版本号，必填。 */
    @NotNull(message = "version 不能为空")
    @Min(value = 0, message = "version 不能为负数")
    private Integer version;
}
