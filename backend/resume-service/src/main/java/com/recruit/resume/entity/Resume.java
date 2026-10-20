package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历（recruit_resume.resume）。
 *
 * <p>sha256 唯一：同一文件重复上传直接命中已有简历。结构化结果存 JSON 字符串，
 * 解析历史不覆盖，见 {@link ResumeVersion}。</p>
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 对外简历编号，如 R{id}。 */
    private String resumeNo;

    private Long personId;

    /** file-service 的文件 ID。 */
    private Long fileId;

    private String fileKey;

    private String fileName;

    /** 原始文件 SHA-256；简历 ≤20MB，file-service 必定计算。 */
    private String sha256;

    /** PORTAL / EMAIL / HR_IMPORT。 */
    private String channel;

    /** PENDING/PARSING/STRUCTURING/INDEXING/SUCCESS/FAILED。 */
    private String parseStatus;

    /** 最新结构化结果（JSON 字符串）。 */
    private String structuredJson;

    /** MinerU 产出的 Markdown 文件 ID。 */
    private Long markdownFileId;

    /** 当前解析版本号，从 0 开始。 */
    private Integer currentVersionNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
