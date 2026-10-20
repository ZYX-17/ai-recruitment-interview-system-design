package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历解析版本（recruit_resume.resume_version）。
 *
 * <p>每次重新解析或人工编辑都追加一行，保留 parser/model/prompt 版本，便于追溯与回滚。</p>
 */
@Data
@TableName("resume_version")
public class ResumeVersion {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long resumeId;

    private Integer versionNo;

    /** 解析器版本，如 mineru-1.x / manual。 */
    private String parserVersion;

    /** LLM 抽取模型；人工版本为空。 */
    private String extractModel;

    private Integer promptVersion;

    /** 该版本的结构化 JSON。 */
    private String structuredJson;

    private Long markdownFileId;

    private LocalDateTime createdAt;
}
