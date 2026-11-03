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
import com.recruit.interview.support.IdSupport;
import com.recruit.interview.support.ScheduleSlotWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 招聘统计：漏斗、面试官工作量、招聘周期（P11-1/P11-2）。
 *
 * <p>只读；数据范围与投递列表一致（ALL/DEPARTMENT/SELF/ASSIGNED）。
 * 时间范围按 Asia/Shanghai 业务日解释，默认最近 30 天、最多 366 天。
 * 漏斗与工作量排除 {@code CANCELLED}，{@code NO_SHOW} 单列不计入完成（ADR-004）。</p>
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    static final String STAGE_APPLIED = "APPLIED";
    static final String STAGE_HIRED = "HIRED";

    private static final long DEFAULT_RANGE_DAYS = 30;
    private static final long MAX_RANGE_DAYS = 366;

    private static final Map<String, String> FALLBACK_NAMES = Map.of(
            STAGE_APPLIED, "投递",
            "OFFER", "Offer",
            STAGE_HIRED, "入职");

    private final StatsMapper statsMapper;
    private final DataScopeService dataScopeService;

    /**
     * 招聘漏斗。
     *
     * @param query 查询条件
     * @return 阶段计数与相邻转化率
     */
    public FunnelView funnel(StatsQuery query) {
        Range range = resolveRange(query.getDateFrom(), query.getDateTo());
        DataScopeService.DataScope scope = dataScopeService.current();
        Long jobId = IdSupport.optionalLong(query.getJobId(), "jobId");

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(STAGE_APPLIED, statsMapper.countApplied(range.from(), range.to(), jobId,
                scope.sqlScope(), scope.deptIds(), scope.userId()));
        for (StageCountRow row : statsMapper.selectStageCounts(range.from(), range.to(), jobId,
                scope.sqlScope(), scope.deptIds(), scope.userId())) {
            if (row.getStageNo() == null || row.getStageNo().isBlank()) {
                continue;
            }
            counts.merge(row.getStageNo(), row.getTotal() == null ? 0L : row.getTotal(), Long::sum);
        }

        Map<String, String> names = new LinkedHashMap<>();
        Set<String> order = new LinkedHashSet<>();
        order.add(STAGE_APPLIED);
        for (StageNameRow def : statsMapper.selectStageNames()) {
            if (def.getStageNo() == null || def.getStageNo().isBlank()) {
                continue;
            }
            names.putIfAbsent(def.getStageNo(),
                    def.getStageName() == null || def.getStageName().isBlank()
                            ? stageName(def.getStageNo()) : def.getStageName());
            order.add(def.getStageNo());
        }
        for (String stageNo : counts.keySet()) {
            order.add(stageNo);
            names.putIfAbsent(stageNo, stageName(stageNo));
        }

        List<FunnelStageView> stages = new ArrayList<>(order.size());
        for (String stageNo : order) {
            stages.add(new FunnelStageView(stageNo, names.getOrDefault(stageNo, stageName(stageNo)),
                    counts.getOrDefault(stageNo, 0L)));
        }
        return new FunnelView(stages, conversionRates(stages));
    }

    /**
     * 面试官工作量。
     *
     * @param query 查询条件
     * @return 面试官维度统计
     */
    public WorkloadView workload(StatsQuery query) {
        Range range = resolveRange(query.getDateFrom(), query.getDateTo());
        DataScopeService.DataScope scope = dataScopeService.current();
        Long interviewerId = IdSupport.optionalLong(query.getInterviewerId(), "interviewerId");
        List<WorkloadItemView> items = new ArrayList<>();
        for (WorkloadRow row : statsMapper.selectWorkload(range.from(), range.to(), interviewerId,
                scope.sqlScope(), scope.deptIds(), scope.userId())) {
            items.add(new WorkloadItemView(row.getInterviewerId(),
                    row.getTotal() == null ? 0L : row.getTotal(),
                    row.getCompleted() == null ? 0L : row.getCompleted(),
                    row.getNoShow() == null ? 0L : row.getNoShow()));
        }
        return new WorkloadView(items);
    }

    /**
     * 招聘周期（投递 → 入职）。
     *
     * @param query 查询条件
     * @return 平均/最短/最长天数
     */
    public TimeToHireView timeToHire(StatsQuery query) {
        Range range = resolveRange(query.getDateFrom(), query.getDateTo());
        DataScopeService.DataScope scope = dataScopeService.current();
        Long jobId = IdSupport.optionalLong(query.getJobId(), "jobId");
        TimeToHireRow row = statsMapper.selectTimeToHire(range.from(), range.to(), jobId,
                scope.sqlScope(), scope.deptIds(), scope.userId());
        if (row == null || row.getSampleCount() == null || row.getSampleCount() == 0) {
            return new TimeToHireView(0L, null, null, null);
        }
        return new TimeToHireView(row.getSampleCount(), days(row.getAvgDays()),
                days(row.getMinDays()), days(row.getMaxDays()));
    }

    private Range resolveRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ScheduleSlotWindow.BUSINESS_ZONE);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS - 1) : from;
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "统计开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new BizException(ErrorCode.PARAM_INVALID, "统计时间范围不能超过 366 天");
        }
        return new Range(utcStart(start), utcStart(end.plusDays(1)));
    }

    private static LocalDateTime utcStart(LocalDate date) {
        return LocalDateTime.ofInstant(ScheduleSlotWindow.startOfDay(date), ZoneOffset.UTC);
    }

    private static Map<String, BigDecimal> conversionRates(List<FunnelStageView> stages) {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        for (int i = 1; i < stages.size(); i++) {
            long previous = stages.get(i - 1).count();
            if (previous <= 0) {
                continue;
            }
            BigDecimal rate = BigDecimal.valueOf(stages.get(i).count())
                    .divide(BigDecimal.valueOf(previous), 4, RoundingMode.HALF_UP);
            rates.put(stages.get(i - 1).stageNo() + "_TO_" + stages.get(i).stageNo(), rate);
        }
        return rates;
    }

    private static String stageName(String stageNo) {
        return FALLBACK_NAMES.getOrDefault(stageNo, stageNo);
    }

    private static BigDecimal days(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record Range(LocalDateTime from, LocalDateTime to) {
    }
}
