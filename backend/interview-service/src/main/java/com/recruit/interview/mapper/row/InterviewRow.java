package com.recruit.interview.mapper.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试列表/日历行：interview 主表 + 投递/岗位快照。
 *
 * <p>列表需要展示岗位、候选人、数据范围归属，但排期服务不跨库查 resume/job 详情，
 * 因此一次 JOIN 出快照字段，避免 N+1。</p>
 */
@Data
public class InterviewRow {

    private Long id;

    private Long applicationId;

    private Integer roundNo;

    private String interviewerIds;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String mode;

    private String roomType;

    private Long roomId;

    private String status;

    private String cancelReason;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Long jobId;

    private String jobTitle;

    private Long personId;

    private String personName;

    private Long deptId;

    private Long ownerHrId;
}
