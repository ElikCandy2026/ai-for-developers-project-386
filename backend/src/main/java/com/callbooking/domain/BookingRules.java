package com.callbooking.domain;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Immutable snapshot of the owner's booking rules.
 */
public record BookingRules(
        String timezone,
        List<DayOfWeek> workingDays,
        LocalTime workdayStart,
        LocalTime workdayEnd,
        int slotStepMinutes,
        int bookingWindowDays,
        int bookingWindowStartOffsetDays,
        int minimumNoticeMinutes,
        int minimumDurationMinutes,
        int maximumDurationMinutes) {

    public BookingRules {
        workingDays = List.copyOf(workingDays);
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public boolean isWorkingDay(DayOfWeek day) {
        return workingDays.contains(day);
    }
}
