package com.callbooking.domain;

import java.time.Instant;

public record Slot(Instant start, Instant end) {
}
