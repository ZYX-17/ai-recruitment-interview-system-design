package com.recruit.interview.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 只需要版本号的流转入参（发布/关闭）。
 */
@Data
public class VersionForm {

    @NotNull
    private Integer version;
}
