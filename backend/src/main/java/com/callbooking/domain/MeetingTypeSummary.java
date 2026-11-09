package com.callbooking.domain;

public record MeetingTypeSummary(
        String id,
        String name,
        String description,
        int durationMinutes) {
}
