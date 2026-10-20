package com.recruit.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历解析任务（recruit_resume.resume_parse_task）。
 *
 * <p>前端按 taskNo 轮询进度；失败可重试，retryCount 上限 3，超过转 DEAD 走补偿。</p>
 */
@Data
@TableName("resume_parse_task")
public class ResumeParseTask {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 对外任务号，如 PT{id}。 */
    private String taskNo;

    private Long resumeId;

    /** ai-service 的任务号（可选）。 */
    private String aiTaskNo;

    /** PENDING/RUNNING/SUCCESS/FAILED/DEAD。 */
    private String status;

    /** PARSE/CHUNK/EMBED/INDEX。 */
    private String stage;

    /** 0~100。 */
    private Integer progress;

    private String errorMsg;

    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
