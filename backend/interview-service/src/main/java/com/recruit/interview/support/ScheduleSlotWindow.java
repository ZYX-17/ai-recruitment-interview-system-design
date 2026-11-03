package com.recruit.interview.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 排期时间槽算法（docs/4 §7.5.1）。
 *
 * <p>口径：</p>
 * <ul>
 *   <li>30 分钟一格，{@code slot_no} 0~47；</li>
 *   <li>{@code slot_date} 按 Asia/Shanghai 业务时区，时间戳本身仍存 UTC；</li>
 *   <li>面试 {@code [start, end)} 覆盖从 {@code start} 到 {@code end-1ms} 的每一格；</li>
 *   <li>起止必须对齐 30 分钟，不做自动取整（否则前端展示与实际占位不一致）。</li>
 * </ul>
 *
 * <p>纯函数，不依赖数据库与 Spring 上下文，便于单测覆盖跨天/边界场景。</p>
 */
public final class ScheduleSlotWindow {

    /** 业务时区：与前端展示、日历查询保持一致。 */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int SLOT_MINUTES = 30;

    private ScheduleSlotWindow() {
    }

    /**
     * 计算面试覆盖的格子。
     *
     * @param start 开始时间（UTC）
     * @param end   结束时间（UTC，开区间）
     * @return 按时间升序的格子列表，至少 1 个
     */
    public static List<ScheduleSlotKey> cover(Instant start, Instant end) {
        validate(start, end);
        List<ScheduleSlotKey> slots = new ArrayList<>();
        Instant cursor = start;
        Instant last = end.minusMillis(1);
        while (!cursor.isAfter(last)) {
            ZonedDateTime local = cursor.atZone(BUSINESS_ZONE);
            int slotNo = local.getHour() * 2 + (local.getMinute() >= SLOT_MINUTES ? 1 : 0);
            slots.add(new ScheduleSlotKey(local.toLocalDate(), slotNo));
            cursor = cursor.plus(SLOT_MINUTES, ChronoUnit.MINUTES);
        }
        return slots;
    }

    /**
     * 校验起止时间。
     *
     * @param start 开始时间
     * @param end   结束时间
     */
    public static void validate(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始/结束时间不能为空");
        }
        if (!end.isAfter(start)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
        if (!aligned(start) || !aligned(end)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "面试起止时间必须对齐 30 分钟");
        }
    }

    /**
     * 是否对齐 30 分钟（秒/纳秒必须为 0）。
     *
     * @param value 时间
     * @return 对齐返回 true
     */
    public static boolean aligned(Instant value) {
        if (value == null) {
            return false;
        }
        ZonedDateTime local = value.atZone(BUSINESS_ZONE);
        return local.getMinute() % SLOT_MINUTES == 0 && local.getSecond() == 0 && local.getNano() == 0;
    }

    /**
     * 业务时区日期。
     *
     * @param value 时间（UTC）
     * @return Asia/Shanghai 日期
     */
    public static LocalDate businessDate(Instant value) {
        return value.atZone(BUSINESS_ZONE).toLocalDate();
    }

    /**
     * 业务时区本地时间。
     *
     * @param value 时间（UTC）
     * @return Asia/Shanghai 本地时间
     */
    public static LocalDateTime businessTime(Instant value) {
        return value.atZone(BUSINESS_ZONE).toLocalDateTime();
    }

    /**
     * 业务日期当天 00:00 对应的 UTC 时间。
     *
     * @param date 业务日期
     * @return UTC 时间
     */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }
}
