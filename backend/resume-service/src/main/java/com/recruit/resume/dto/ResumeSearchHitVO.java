package com.recruit.resume.dto;

import com.recruit.resume.entity.Person;
import com.recruit.resume.entity.Resume;

import java.util.List;

/**
 * 简历搜索命中（按简历聚合，而不是按 chunk 展示）。
 *
 * @param score      混合检索 RRF 分数（mysql-like 兜底时为关键词命中块数）
 * @param highlights 最相关的 1~2 个块摘要，便于前端展示命中原因
 */
public record ResumeSearchHitVO(
        String resumeId,
        String resumeNo,
        String personId,
        String personName,
        String fileName,
        String city,
        String education,
        Integer workYears,
        String parseStatus,
        double score,
        List<String> highlights
) {

    public static ResumeSearchHitVO from(Resume resume, Person person, double score,
                                         List<String> highlights) {
        return new ResumeSearchHitVO(
                String.valueOf(resume.getId()),
                resume.getResumeNo(),
                resume.getPersonId() == null ? null : String.valueOf(resume.getPersonId()),
                person == null ? null : person.getName(),
                resume.getFileName(),
                person == null ? null : person.getCity(),
                person == null ? null : person.getEducation(),
                person == null ? null : person.getWorkYears(),
                resume.getParseStatus(),
                score,
                highlights == null ? List.of() : List.copyOf(highlights));
    }
}
