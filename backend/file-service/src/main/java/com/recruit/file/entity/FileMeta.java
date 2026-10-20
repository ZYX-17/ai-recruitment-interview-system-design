package com.recruit.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据（recruit_file.file_meta）。
 *
 * <p>对象实体存 MinIO，本表只存索引与校验结果。主键由 MyBatis-Plus 雪花算法生成，
 * 对外接口一律转成字符串，避免前端 JS Number 精度丢失。</p>
 */
@Data
@TableName("file_meta")
public class FileMeta {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务文件键：预签名、完成、登记都以它关联，全局唯一。 */
    private String fileKey;

    /** 所在桶。 */
    private String bucket;

    /** MinIO 对象路径，如 resume/2026/10/20/xxx.pdf。 */
    private String objectKey;

    /** 内容 SHA-256（小写 hex）；未计算时为空串（列 NOT NULL）。 */
    private String sha256;

    /** 规范化后的 MIME。 */
    private String mime;

    /** 文件字节数。 */
    private Long size;

    /** 业务类型：RESUME/RECORDING/ANSWER_VIDEO/EVIDENCE/AVATAR/ATTACHMENT。 */
    private String bizType;

    /** 业务主键（可为空，由调用方解释）。 */
    private String bizId;

    /** 原始文件名。 */
    private String fileName;

    /** 上传人用户 ID。 */
    private Long uploaderId;

    /** UPLOADING / OK / REJECTED / DELETED。 */
    private String status;

    /** 拒绝原因（MIME/大小/校验失败）。 */
    private String rejectReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 乐观锁版本号。 */
    @Version
    private Integer version;
}
