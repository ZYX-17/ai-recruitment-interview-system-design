package com.recruit.interview.service;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import com.recruit.interview.dto.FunnelStageView;
import com.recruit.interview.dto.FunnelView;
import com.recruit.interview.dto.StatsQuery;
import com.recruit.interview.dto.TimeToHireView;
import com.recruit.interview.dto.WorkloadItemView;
import com.recruit.interview.dto.WorkloadView;
import com.recruit.interview.mapper.StatsMapper;
import com.recruit.interview.mapper.row.StageCountRow;
import com.recruit.interview.mapper.row.StageNameRow;
import com.recruit.interview.mapper.row.TimeToHireRow;
import com.recruit.interview.mapper.row.WorkloadRow;
import com.recruit.interview.support.ScheduleSlotWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 招聘统计（P11-1/P11-2）。
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private StatsMapper statsMapper;
    @Mock
    private DataScopeService dataScopeService;

    private StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(statsMapper, dataScopeService);
    }

    @Test
    void funnelBuildsStagesInTemplateOrderAndRates() {
        when(dataScopeService.current()).thenReturn(allScope());
        when(statsMapper.countApplied(any(), any(), isNull(), eq("ALL"), anyList(), anyLong()))
                .thenReturn(120L);
        when(statsMapper.selectStageCounts(any(), any(), isNull(), eq("ALL"), anyList(), anyLong()))
                .thenReturn(List.of(stageCount("SCREEN", 80), stageCount("FIRST", 40),
                        stageCount("OFFER", 8)));
        when(statsMapper.selectStageNames()).thenReturn(List.of(
                stageName("SCREEN", "初筛", 1),
                stageName("FIRST", "一面", 2),
                stageName("OFFER", "Offer", 3)));

        FunnelView view = service.funnel(new StatsQuery());

        assertThat(view.stages()).extracting(FunnelStageView::stageNo)
                .containsExactly("APPLIED", "SCREEN", "FIRST", "OFFER");
        assertThat(view.stages()).extracting(FunnelStageView::count)
                .containsExactly(120L, 80L, 40L, 8L);
        assertThat(view.stages().get(0).stageName()).isEqualTo("投递");
        assertThat(view.stages().get(1).stageName()).isEqualTo("初筛");
        assertThat(view.conversionRates())
                .containsEntry("APPLIED_TO_SCREEN", new BigDecimal("0.6667"))
                .containsEntry("SCREEN_TO_FIRST", new BigDecimal("0.5000"))
                .containsEntry("FIRST_TO_OFFER", new BigDecimal("0.2000"));
    }

    @Test
    void funnelAppendsStageOnlyPresentInHistory() {
        when(dataScopeService.current()).thenReturn(allScope());
        when(statsMapper.countApplied(any(), any(), any(), anyString(), anyList(), anyLong()))
                .thenReturn(10L);
        when(statsMapper.selectStageCounts(any(), any(), any(), anyString(), anyList(), anyLong()))
                .thenReturn(List.of(stageCount("HIRED", 3)));
        when(statsMapper.selectStageNames()).thenReturn(List.of());

        FunnelView view = service.funnel(new StatsQuery());

        assertThat(view.stages()).extracting(FunnelStageView::stageNo)
                .containsExactly("APPLIED", "HIRED");
        assertThat(view.stages().get(1).stageName()).isEqualTo("入职");
        assertThat(view.conversionRates()).containsEntry("APPLIED_TO_HIRED", new BigDecimal("0.3000"));
    }

    @Test
    void defaultRangeCoversLastThirtyBusinessDays() {
        when(dataScopeService.current()).thenReturn(allScope());
        when(statsMapper.countApplied(any(), any(), any(), anyString(), anyList(), anyLong()))
                .thenReturn(0L);
        when(statsMapper.selectStageCounts(any(), any(), any(), anyString(), anyList(), anyLong()))
                .thenReturn(List.of());
        when(statsMapper.selectStageNames()).thenReturn(List.of());

        service.funnel(new StatsQuery());

        LocalDate today = LocalDate.now(ScheduleSlotWindow.BUSINESS_ZONE);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(statsMapper).countApplied(fromCaptor.capture(), toCaptor.capture(),
                isNull(), eq("ALL"), anyList(), eq(10001L));
        assertThat(fromCaptor.getValue()).isEqualTo(utcStart(today.minusDays(29)));
        assertThat(toCaptor.getValue()).isEqualTo(utcStart(today.plusDays(1)));
    }

    @Test
    void rejectsRangeLongerThanOneYear() {
        StatsQuery query = new StatsQuery();
        query.setDateFrom(LocalDate.of(2026, 1, 1));
        query.setDateTo(LocalDate.of(2027, 1, 2));

        assertThatThrownBy(() -> service.funnel(query))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void rejectsFromAfterTo() {
        StatsQuery query = new StatsQuery();
        query.setDateFrom(LocalDate.of(2026, 10, 20));
        query.setDateTo(LocalDate.of(2026, 10, 19));

        assertThatThrownBy(() -> service.workload(query))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void departmentScopeIsForwardedToMapper() {
        when(dataScopeService.current()).thenReturn(
                new DataScopeService.DataScope("DEPARTMENT", 20001L, List.of(20001L, 20002L), 10001L));
        when(statsMapper.selectWorkload(any(), any(), isNull(), eq("DEPARTMENT"),
                eq(List.of(20001L, 20002L)), eq(10001L))).thenReturn(List.of());

        service.workload(new StatsQuery());

        verify(statsMapper).selectWorkload(any(), any(), isNull(), eq("DEPARTMENT"),
                eq(List.of(20001L, 20002L)), eq(10001L));
    }

    @Test
    void workloadMapsNullCountersToZero() {
        when(dataScopeService.current()).thenReturn(allScope());
        WorkloadRow row = new WorkloadRow();
        row.setInterviewerId("10002");
        row.setTotal(5L);
        when(statsMapper.selectWorkload(any(), any(), eq(10002L), anyString(), anyList(), anyLong()))
                .thenReturn(List.of(row));
        StatsQuery query = new StatsQuery();
        query.setInterviewerId("10002");

        WorkloadView view = service.workload(query);

        assertThat(view.items()).containsExactly(new WorkloadItemView("10002", 5L, 0L, 0L));
    }

    @Test
    void timeToHireRoundsDaysAndHandlesEmpty() {
        when(dataScopeService.current()).thenReturn(allScope());
        TimeToHireRow row = new TimeToHireRow();
        row.setSampleCount(4L);
        row.setAvgDays(new BigDecimal("12.345"));
        row.setMinDays(new BigDecimal("3.1"));
        row.setMaxDays(new BigDecimal("30"));
        when(statsMapper.selectTimeToHire(any(), any(), isNull(), anyString(), anyList(), anyLong()))
                .thenReturn(row);

        TimeToHireView view = service.timeToHire(new StatsQuery());
        assertThat(view.sampleCount()).isEqualTo(4L);
        assertThat(view.avgDays()).isEqualTo(new BigDecimal("12.35"));
        assertThat(view.minDays()).isEqualTo(new BigDecimal("3.10"));
        assertThat(view.maxDays()).isEqualTo(new BigDecimal("30.00"));

        when(statsMapper.selectTimeToHire(any(), any(), isNull(), anyString(), anyList(), anyLong()))
                .thenReturn(null);
        TimeToHireView empty = service.timeToHire(new StatsQuery());
        assertThat(empty.sampleCount()).isZero();
        assertThat(empty.avgDays()).isNull();
    }

    private static DataScopeService.DataScope allScope() {
        return new DataScopeService.DataScope("ALL", 20001L, List.of(20001L), 10001L);
    }

    private static StageCountRow stageCount(String stageNo, long total) {
        StageCountRow row = new StageCountRow();
        row.setStageNo(stageNo);
        row.setTotal(total);
        return row;
    }

    private static StageNameRow stageName(String stageNo, String name, int ord) {
        StageNameRow row = new StageNameRow();
        row.setStageNo(stageNo);
        row.setStageName(name);
        row.setOrd(ord);
        return row;
    }

    private static LocalDateTime utcStart(LocalDate date) {
        Instant instant = ScheduleSlotWindow.startOfDay(date);
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
