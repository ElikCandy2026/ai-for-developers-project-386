package com.callbooking.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

class BookingRepositoryTest {

    @Test
    void mapsTheOverlapTriggerBySqliteResultCode() {
        SQLiteException sqlite = new SQLiteException(
                "constraint failed", SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER);
        UncategorizedSQLException wrapped = new UncategorizedSQLException(
                "insert booking", "INSERT INTO bookings", sqlite);

        assertThat(BookingRepository.isTriggerConflict(wrapped)).isTrue();
    }

    @Test
    void mapsTheOverlapTriggerByMessageWhenTheResultCodeIsLost() {
        assertThat(BookingRepository.isTriggerConflict(
                new RuntimeException("SQLITE_CONSTRAINT: booking_overlap"))).isTrue();
    }

    @Test
    void doesNotMistakeOtherDatabaseFailuresForASlotConflict() {
        SQLiteException busy = new SQLiteException("database is locked", SQLiteErrorCode.SQLITE_BUSY);

        assertThat(BookingRepository.isTriggerConflict(busy)).isFalse();
        assertThat(BookingRepository.isTriggerConflict(
                new RuntimeException("connection refused"))).isFalse();
    }
}
