package com.callbooking.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Occupied interval used when checking slots for overlap. Adjacent intervals do
 * not overlap: the end of one may equal the start of another.
 */
public record BookingInterval(Instant start, Instant end) {

    public BookingInterval {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return otherStart.isBefore(end) && otherEnd.isAfter(start);
    }
}
