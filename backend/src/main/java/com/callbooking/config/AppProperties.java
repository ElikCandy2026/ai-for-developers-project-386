package com.callbooking.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application configuration. The booking rules are exposed through GET /api/owner,
 * so they live in one place and are not duplicated in the frontend.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String databasePath,
        boolean seedDemoData,
        Owner owner) {

    public record Owner(
            String id,
            String displayName,
            String email,
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
    }
}
