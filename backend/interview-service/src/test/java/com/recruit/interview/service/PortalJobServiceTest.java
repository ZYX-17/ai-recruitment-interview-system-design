package com.recruit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.common.result.PageResult;
import com.recruit.interview.dto.PortalJobPageQuery;
import com.recruit.interview.dto.PortalJobView;
import com.recruit.interview.entity.Job;
import com.recruit.interview.mapper.JobMapper;
import com.recruit.interview.mapper.row.JobRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门户岗位（I-10）：只返回 PUBLISHED。
 */
@ExtendWith(MockitoExtension.class)
class PortalJobServiceTest {

    @Mock
    private JobMapper jobMapper;

    private PortalJobService service;

    @BeforeEach
    void setUp() {
        service = new PortalJobService(jobMapper, new ObjectMapper());
    }

    @Test
    void pageMapsPublishedJobsWithoutInternalFields() {
        when(jobMapper.countPublishedPage("Java")).thenReturn(1L);
        when(jobMapper.selectPublishedPage(eq("Java"), eq(20), eq(0L))).thenReturn(List.of(row()));
        PortalJobPageQuery query = new PortalJobPageQuery();
        query.setKeyword(" Java ");

        PageResult<PortalJobView> page = service.page(query);

        assertThat(page.getTotal()).isEqualTo(1);
        PortalJobView view = page.getList().get(0);
        assertThat(view.id()).isEqualTo("50001");
        assertThat(view.title()).isEqualTo("Java 开发工程师");
        assertThat(view.deptName()).isEqualTo("研发中心");
        assertThat(view.requirements().get("years").asInt()).isEqualTo(3);
    }

    @Test
    void pageSkipsRowsWhenEmpty() {
        when(jobMapper.countPublishedPage(isNull())).thenReturn(0L);

        PageResult<PortalJobView> page = service.page(new PortalJobPageQuery());

        assertThat(page.getList()).isEmpty();
        verify(jobMapper, never()).selectPublishedPage(isNull(), anyInt(), anyLong());
    }

    @Test
    void detailRejectsDraftJob() {
        Job job = new Job();
        job.setId(50003L);
        job.setStatus(JobService.STATUS_DRAFT);
        when(jobMapper.selectById(50003L)).thenReturn(job);

        assertThatThrownBy(() -> service.detail(50003L))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void detailReturnsPublishedJob() {
        Job job = new Job();
        job.setId(50001L);
        job.setJobCode("JOB-DEMO-JAVA");
        job.setTitle("Java 开发工程师");
        job.setStatus(JobService.STATUS_PUBLISHED);
        job.setRequirementsJson("{\"years\":3}");
        when(jobMapper.selectById(50001L)).thenReturn(job);

        PortalJobView view = service.detail(50001L);

        assertThat(view.id()).isEqualTo("50001");
        assertThat(view.requirements().get("years").asInt()).isEqualTo(3);
    }

    private static JobRow row() {
        JobRow row = new JobRow();
        row.setId(50001L);
        row.setJobCode("JOB-DEMO-JAVA");
        row.setTitle("Java 开发工程师");
        row.setDeptName("研发中心");
        row.setRequirementsJson("{\"years\":3}");
        row.setHeadcount(2);
        row.setSalaryRange("20k-35k");
        row.setStatus(JobService.STATUS_PUBLISHED);
        row.setPublishedAt(LocalDateTime.of(2026, 10, 1, 1, 0));
        return row;
    }
}
