package com.callbooking.persistence;

/**
 * Unexpected storage failure. Driver-specific exceptions stay inside the
 * persistence module; the application only sees this unchecked wrapper (or a
 * {@link BookingInsertResult} when the slot is taken).
 */
public class BookingPersistenceException extends RuntimeException {

    public BookingPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
