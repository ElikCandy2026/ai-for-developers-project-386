package com.callbooking.domain;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Immutable snapshot of the owner's calendar rules. The scheduling policy reads
 * it; nothing else derives window, working hours or grid from configuration.
 */
public record OwnerSchedule(
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

    public OwnerSchedule {
        workingDays = List.copyOf(workingDays);
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public boolean isWorkingDay(DayOfWeek day) {
        return workingDays.contains(day);
    }
}
