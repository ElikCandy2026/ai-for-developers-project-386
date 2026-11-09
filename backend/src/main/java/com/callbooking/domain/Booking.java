package com.callbooking.domain;

import java.time.Instant;

public record Booking(
        String id,
        MeetingTypeSummary meetingType,
        String guestName,
        String guestEmail,
        String notes,
        Instant start,
        Instant end,
        Instant createdAt) {
}
