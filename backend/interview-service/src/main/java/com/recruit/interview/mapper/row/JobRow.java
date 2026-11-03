package com.recruit.interview.mapper.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位分页行（含投递数）。
 */
@Data
public class JobRow {

    private Long id;

    private String jobCode;

    private String title;

    private Long deptId;

    private String deptName;

    private String jdText;

    private String requirementsJson;

    private Integer headcount;

    private String salaryRange;

    private String status;

    private Long ownerHrId;

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Long applicationCount;
}
