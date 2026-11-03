package com.recruit.interview.support;

import com.recruit.common.exception.BizException;
import com.recruit.common.result.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 排期时间槽算法（P5-1）。
 */
class ScheduleSlotWindowTest {

    @Test
    void coverMapsShanghaiLocalTimeToSlotNumbers() {
        List<ScheduleSlotKey> slots = ScheduleSlotWindow.cover(
                Instant.parse("2026-10-20T01:00:00Z"),
                Instant.parse("2026-10-20T02:00:00Z"));

        assertThat(slots).containsExactly(
                new ScheduleSlotKey(LocalDate.of(2026, 10, 20), 18),
                new ScheduleSlotKey(LocalDate.of(2026, 10, 20), 19));
    }

    @Test
    void halfHourWindowCoversExactlyOneSlot() {
        List<ScheduleSlotKey> slots = ScheduleSlotWindow.cover(
                Instant.parse("2026-10-20T01:00:00Z"),
                Instant.parse("2026-10-20T01:30:00Z"));

        assertThat(slots).containsExactly(new ScheduleSlotKey(LocalDate.of(2026, 10, 20), 18));
    }

    @Test
    void endIsExclusiveUsingEndMinusOneMillisecond() {
        List<ScheduleSlotKey> slots = ScheduleSlotWindow.cover(
                Instant.parse("2026-10-20T01:30:00Z"),
                Instant.parse("2026-10-20T03:00:00Z"));

        assertThat(slots).extracting(ScheduleSlotKey::slotNo).containsExactly(19, 20, 21);
    }

    @Test
    void crossMidnightSplitsIntoTwoBusinessDates() {
        List<ScheduleSlotKey> slots = ScheduleSlotWindow.cover(
                Instant.parse("2026-10-20T15:30:00Z"),
                Instant.parse("2026-10-20T16:30:00Z"));

        assertThat(slots).containsExactly(
                new ScheduleSlotKey(LocalDate.of(2026, 10, 20), 47),
                new ScheduleSlotKey(LocalDate.of(2026, 10, 21), 0));
    }

    @Test
    void businessDateUsesShanghaiNotUtc() {
        assertThat(ScheduleSlotWindow.businessDate(Instant.parse("2026-10-20T17:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 10, 21));
    }

    @Test
    void rejectsTimeNotAlignedToThirtyMinutes() {
        assertThatThrownBy(() -> ScheduleSlotWindow.cover(
                Instant.parse("2026-10-20T01:15:00Z"),
                Instant.parse("2026-10-20T02:00:00Z")))
                .isInstanceOfSatisfying(BizException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }

    @Test
    void rejectsSecondsOrNanos() {
        assertThat(ScheduleSlotWindow.aligned(Instant.parse("2026-10-20T01:00:30Z"))).isFalse();
        assertThat(ScheduleSlotWindow.aligned(Instant.parse("2026-10-20T01:00:00.001Z"))).isFalse();
        assertThat(ScheduleSlotWindow.aligned(Instant.parse("2026-10-20T01:00:00Z"))).isTrue();
    }

    @Test
    void rejectsEmptyOrReversedWindow() {
        assertThatThrownBy(() -> ScheduleSlotWindow.cover(
                Instant.parse("2026-10-20T02:00:00Z"),
                Instant.parse("2026-10-20T02:00:00Z")))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> ScheduleSlotWindow.cover(null, Instant.now()))
                .isInstanceOf(BizException.class);
    }
}
