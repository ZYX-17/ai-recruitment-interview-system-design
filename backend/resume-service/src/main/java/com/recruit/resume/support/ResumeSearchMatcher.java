package com.recruit.resume.support;

import com.recruit.resume.dto.SearchFilters;
import com.recruit.resume.entity.Person;
import com.recruit.resume.entity.Resume;

import java.util.List;
import java.util.Locale;

/**
 * 简历搜索的硬过滤：解析状态、城市、学历、工作年限、技能。
 *
 * <p>属性来自 person，技能在分块内容里做包含匹配（ES 索引与 mysql-like 兜底共用同一套规则，
 * 避免两条链路结果不一致）。</p>
 */
public final class ResumeSearchMatcher {

    private ResumeSearchMatcher() {
    }

    public static boolean matches(Resume resume, Person person, SearchFilters filters, String content) {
        if (resume == null) {
            return false;
        }
        SearchFilters effective = filters == null
                ? new SearchFilters(null, null, null, null, null, null)
                : filters;

        String status = hasText(effective.parseStatus())
                ? effective.parseStatus().trim()
                : ResumeStatus.SUCCESS;
        if (!"ALL".equalsIgnoreCase(status)
                && !status.equalsIgnoreCase(resume.getParseStatus())) {
            return false;
        }

        if (person == null) {
            return !hasPersonFilter(effective);
        }
        if (hasText(effective.city())
                && !effective.city().trim().equalsIgnoreCase(person.getCity())) {
            return false;
        }
        if (hasText(effective.education())
                && !effective.education().trim().equalsIgnoreCase(person.getEducation())) {
            return false;
        }
        Integer workYears = person.getWorkYears();
        if (effective.workYearsMin() != null
                && (workYears == null || workYears < effective.workYearsMin())) {
            return false;
        }
        if (effective.workYearsMax() != null
                && (workYears == null || workYears > effective.workYearsMax())) {
            return false;
        }

        List<String> skills = effective.skills();
        if (skills != null && !skills.isEmpty()) {
            String lowerContent = content == null ? "" : content.toLowerCase(Locale.ROOT);
            for (String skill : skills) {
                if (!hasText(skill)) {
                    continue;
                }
                if (!lowerContent.contains(skill.trim().toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean hasPersonFilter(SearchFilters filters) {
        return filters != null && (hasText(filters.city())
                || hasText(filters.education())
                || filters.workYearsMin() != null
                || filters.workYearsMax() != null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
