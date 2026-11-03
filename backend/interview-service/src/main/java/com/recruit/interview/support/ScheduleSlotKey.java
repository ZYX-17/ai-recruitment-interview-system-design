package com.recruit.interview.support;

import java.time.LocalDate;

/**
 * 一个排期格子：业务时区日期 + 30 分钟序号。
 *
 * <p>{@code slotNo} 取值 0~47，{@code slotNo = hour*2 + (minute>=30 ? 1 : 0)}，
 * 对应 Asia/Shanghai 当天的 00:00、00:30 ... 23:30。</p>
 *
 * @param slotDate 业务时区日期
 * @param slotNo   30 分钟序号
 */
public record ScheduleSlotKey(LocalDate slotDate, int slotNo) {
}
