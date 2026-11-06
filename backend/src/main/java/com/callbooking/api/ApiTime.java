package com.callbooking.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ApiTime {

    private ApiTime() {
    }

    public static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant instant(OffsetDateTime dateTime) {
        return dateTime.toInstant();
    }
}
