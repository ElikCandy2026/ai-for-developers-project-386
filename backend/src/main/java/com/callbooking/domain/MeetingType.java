package com.callbooking.domain;

import java.time.Instant;

public record MeetingType(
        String id,
        String name,
        String description,
        int durationMinutes,
        Instant createdAt) {
}
