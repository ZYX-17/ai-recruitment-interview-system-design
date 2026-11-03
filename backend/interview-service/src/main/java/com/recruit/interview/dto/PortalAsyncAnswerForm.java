package com.recruit.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 单题作答入参：只传 file-service 文件 ID，不传二进制。
 */
@Data
public class PortalAsyncAnswerForm {

    @NotBlank
    private String questionId;

    private String videoFileId;

    private String audioFileId;

    @Min(0)
    @Max(7200)
    private Integer answerSec;
}
