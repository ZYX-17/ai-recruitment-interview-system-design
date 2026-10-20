package com.recruit.resume.dto;

import com.recruit.resume.entity.Person;
import com.recruit.resume.entity.Resume;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 简历详情视图。ID 全部转字符串；时间转 UTC ISO-8601。
 */
public record ResumeVO(
        String id,
        String resumeNo,
        String personId,
        String personName,
        String fileId,
        String fileName,
        String sha256,
        String channel,
        String parseStatus,
        String structuredJson,
        String markdownFileId,
        Integer currentVersionNo,
        Integer version,
        String createdAt,
        String updatedAt
) {

    public static ResumeVO from(Resume resume, Person person) {
        return new ResumeVO(
                String.valueOf(resume.getId()),
                resume.getResumeNo(),
                resume.getPersonId() == null ? null : String.valueOf(resume.getPersonId()),
                person == null ? null : person.getName(),
                resume.getFileId() == null ? null : String.valueOf(resume.getFileId()),
                resume.getFileName(),
                resume.getSha256(),
                resume.getChannel(),
                resume.getParseStatus(),
                resume.getStructuredJson(),
                resume.getMarkdownFileId() == null ? null : String.valueOf(resume.getMarkdownFileId()),
                resume.getCurrentVersionNo(),
                resume.getVersion(),
                utc(resume.getCreatedAt()),
                utc(resume.getUpdatedAt()));
    }

    private static String utc(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC).toString();
    }
}
