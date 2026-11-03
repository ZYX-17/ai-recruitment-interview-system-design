package com.recruit.auth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改字典项入参（X-11，PUT 全量语义）。
 *
 * <p>字典类型与编码不可改；{@code dictLabel}/{@code sort}/{@code status} 传 null 表示保持原值，
 * {@code remark} 传 null 表示清空备注。</p>
 */
@Data
public class UpdateDictForm {

    /** 展示文案；为空表示不修改。 */
    @Size(max = 128, message = "dictLabel 长度不能超过 128")
    private String dictLabel;

    /** 排序；为空表示不修改。 */
    @Min(value = 0, message = "sort 不能为负数")
    private Integer sort;

    /** 状态；为空表示不修改。 */
    @Pattern(regexp = "ACTIVE|DISABLED", message = "status 只能是 ACTIVE/DISABLED")
    private String status;

    /** 备注；null 表示清空。 */
    @Size(max = 255, message = "remark 长度不能超过 255")
    private String remark;

    /** 乐观锁版本号，必填。 */
    @NotNull(message = "version 不能为空")
    @Min(value = 0, message = "version 不能为负数")
    private Integer version;
}
