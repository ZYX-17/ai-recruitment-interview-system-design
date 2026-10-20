package com.recruit.resume.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建简历（上传完成后由前端调用）。
 *
 * <p>文件必须先经 file-service 完成上传并置为 OK；本接口只信任 fileId，
 * 文件大小/SHA-256/文件名以 file-service 元数据为准。personId 为空时按姓名新建候选人。</p>
 */
public record CreateResumeRequest(

        @NotNull(message = "fileId 不能为空")
        Long fileId,

        /** 可选：调用方回传的 fileKey，用于与预签名记录做一致性校验。 */
        @Size(max = 64, message = "fileKey 过长")
        String fileKey,

        /** PORTAL / EMAIL / HR_IMPORT，默认 PORTAL。 */
        @Size(max = 32, message = "channel 过长")
        String channel,

        /** 可选：挂到已有候选人；为空则新建。 */
        Long personId,

        @Size(max = 64, message = "name 过长")
        String name,

        @Size(max = 32, message = "phone 过长")
        String phone,

        @Email(message = "email 格式不正确")
        @Size(max = 128, message = "email 过长")
        String email,

        @Size(max = 64, message = "city 过长")
        String city,

        @Size(max = 32, message = "education 过长")
        String education,

        @Min(value = 0, message = "workYears 不能为负")
        @Max(value = 60, message = "workYears 不合理")
        Integer workYears
) {
}
