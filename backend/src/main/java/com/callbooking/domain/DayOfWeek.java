package com.callbooking.domain;

/**
 * Day of week in the owner's calendar. Kept in the domain so calendar rules do
 * not depend on the transport contract's enum.
 */
public enum DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY;

    public static DayOfWeek from(java.time.DayOfWeek dayOfWeek) {
        return valueOf(dayOfWeek.name());
    }

    public java.time.DayOfWeek toJavaTime() {
        return java.time.DayOfWeek.valueOf(name());
    }
}
