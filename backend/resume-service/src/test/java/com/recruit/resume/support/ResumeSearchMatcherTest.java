package com.recruit.resume.support;

import com.recruit.resume.dto.SearchFilters;
import com.recruit.resume.entity.Person;
import com.recruit.resume.entity.Resume;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeSearchMatcherTest {

    private static final String CONTENT = "Java Spring MySQL 微服务";

    @Test
    void defaultOnlyMatchesSuccessResumes() {
        assertTrue(ResumeSearchMatcher.matches(resume("SUCCESS"), person(), null, CONTENT));
        assertFalse(ResumeSearchMatcher.matches(resume("FAILED"), person(), null, CONTENT));
    }

    @Test
    void parseStatusAllBypassesStatusFilter() {
        SearchFilters filters = new SearchFilters(null, null, null, null, null, "ALL");
        assertTrue(ResumeSearchMatcher.matches(resume("FAILED"), person(), filters, CONTENT));
    }

    @Test
    void filtersCityEducationAndWorkYears() {
        SearchFilters filters = new SearchFilters("上海", "本科", 3, 8, null, null);
        assertTrue(ResumeSearchMatcher.matches(resume("SUCCESS"), person(), filters, CONTENT));

        SearchFilters wrongCity = new SearchFilters("北京", "本科", 3, 8, null, null);
        assertFalse(ResumeSearchMatcher.matches(resume("SUCCESS"), person(), wrongCity, CONTENT));

        SearchFilters tooSenior = new SearchFilters("上海", "本科", 10, 20, null, null);
        assertFalse(ResumeSearchMatcher.matches(resume("SUCCESS"), person(), tooSenior, CONTENT));
    }

    @Test
    void skillsAreCaseInsensitiveAndAllRequired() {
        SearchFilters filters = new SearchFilters(null, null, null, null,
                List.of("java", "spring"), null);
        assertTrue(ResumeSearchMatcher.matches(resume("SUCCESS"), person(), filters, CONTENT));

        SearchFilters missing = new SearchFilters(null, null, null, null,
                List.of("Java", "Kafka"), null);
        assertFalse(ResumeSearchMatcher.matches(resume("SUCCESS"), person(), missing, CONTENT));
    }

    @Test
    void personFilterFailsWhenPersonMissing() {
        SearchFilters filters = new SearchFilters("上海", null, null, null, null, null);
        assertFalse(ResumeSearchMatcher.matches(resume("SUCCESS"), null, filters, CONTENT));
        assertTrue(ResumeSearchMatcher.matches(resume("SUCCESS"), null, null, CONTENT));
    }

    private Resume resume(String parseStatus) {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setParseStatus(parseStatus);
        return resume;
    }

    private Person person() {
        Person person = new Person();
        person.setId(7L);
        person.setCity("上海");
        person.setEducation("本科");
        person.setWorkYears(5);
        return person;
    }
}
