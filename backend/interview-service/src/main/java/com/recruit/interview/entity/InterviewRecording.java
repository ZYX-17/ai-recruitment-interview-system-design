package com.recruit.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试录像登记（{@code interview_recording}）：消费 {@code recording.completed} 落库。
 *
 * <p>{@code record_id} 唯一，重复事件按幂等处理；文件本身在 file-service/MinIO。</p>
 */
@Data
@TableName("interview_recording")
public class InterviewRecording {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long interviewId;

    private Long roomId;

    private String recordId;

    private String fileKey;

    private Integer durationSec;

    private LocalDateTime createdAt;
}
