package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增部门入参。
 *
 * <p>{@code parentId} 为空表示根部门；{@code ancestors} 由服务端按父级路径维护，
 * 不接受客户端提交。同级部门名称不允许重复。</p>
 */
@Data
public class CreateDeptForm {

    /** 父部门 ID；null 表示根部门。 */
    private Long parentId;

    /** 部门名称。 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 64, message = "部门名称长度不能超过 64")
    private String deptName;

    /** 排序，默认 0。 */
    @Min(value = 0, message = "sort 不能为负数")
    private Integer sort;

    /** 状态，默认 ACTIVE。 */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能是 ACTIVE/DISABLED")
    private String status;
}
