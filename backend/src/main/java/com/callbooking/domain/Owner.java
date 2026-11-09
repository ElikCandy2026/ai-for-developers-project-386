package com.callbooking.domain;

public record Owner(String id, String displayName, String email, BookingRules rules) {
}
