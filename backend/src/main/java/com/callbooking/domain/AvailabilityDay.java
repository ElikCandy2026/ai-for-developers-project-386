package com.callbooking.domain;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityDay(LocalDate date, DayOfWeek weekday, List<Slot> slots) {

    public AvailabilityDay {
        slots = List.copyOf(slots);
    }
}
