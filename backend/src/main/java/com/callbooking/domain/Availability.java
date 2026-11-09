package com.callbooking.domain;

import java.time.LocalDate;
import java.util.List;

public record Availability(
        MeetingTypeSummary meetingType,
        String timezone,
        LocalDate windowStartDate,
        LocalDate windowEndDate,
        int slotStepMinutes,
        int durationMinutes,
        List<AvailabilityDay> days) {

    public Availability {
        days = List.copyOf(days);
    }
}
