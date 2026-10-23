package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历分块（recruit_resume.resume_chunk）。
 *
 * <p>与 ES 文档一一对应；ES 重建时以本表为准。表格块保留表头，文本块带标题路径前缀。</p>
 */
@Data
@TableName("resume_chunk")
public class ResumeChunk {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long resumeId;

    private Integer chunkNo;

    /** TEXT / TABLE。 */
    private String chunkType;

    /** 标题路径，如 工作经历/腾讯。 */
    private String sectionPath;

    private String content;

    /** ES resume_vec 文档 ID，如 {resumeId}-{chunkNo}。 */
    private String esDocId;

    private String embeddingModel;

    private LocalDateTime createdAt;
}
