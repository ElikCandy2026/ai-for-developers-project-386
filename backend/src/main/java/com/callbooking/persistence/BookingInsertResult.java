package com.callbooking.persistence;

import com.callbooking.domain.Booking;

/**
 * Outcome of {@link BookingRepository#tryInsert(Booking)}. The persistence module
 * answers the only question the application needs: was the booking stored, or did
 * another booking win the interval first?
 */
public sealed interface BookingInsertResult {

    record Inserted(Booking booking) implements BookingInsertResult {
    }

    record SlotTaken() implements BookingInsertResult {
    }
}
