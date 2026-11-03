package com.recruit.notify.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 模板修改入参。修改不覆盖历史，而是新增 version+1。
 */
@Data
public class TemplateUpdateForm {

    @NotBlank
    private String titleTemplate;

    @NotBlank
    private String contentTemplate;

    /** 变量白名单；占位符必须全部在白名单中。 */
    private List<String> variables;

    @Min(0)
    private int version;
}
