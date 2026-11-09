package com.callbooking.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import com.callbooking.domain.Booking;
import com.callbooking.domain.MeetingTypeSummary;

class BookingRepositoryTest {

    private static final PlatformTransactionManager NO_OP_TRANSACTIONS =
            new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };

    private static SQLiteException triggerFailure() {
        return new SQLiteException(
                "constraint failed", SQLiteErrorCode.SQLITE_CONSTRAINT_TRIGGER);
    }

    private static UncategorizedSQLException triggerException() {
        return new UncategorizedSQLException(
                "insert booking", "INSERT INTO bookings", triggerFailure());
    }

    private static Booking booking() {
        Instant start = Instant.parse("2026-11-09T07:00:00Z");
        return new Booking(
                "booking-1",
                new MeetingTypeSummary("type-1", "Знакомство", "", 30),
                "Гость",
                "guest@example.com",
                null,
                start,
                start.plusSeconds(1800),
                start);
    }

    @Test
    void mapsTheOverlapTriggerBySqliteResultCode() {
        SQLiteException sqlite = triggerFailure();
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

    @Test
    void returnsSlotTakenWhenTheTriggerWinsTheRace() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        doThrow(triggerException()).when(jdbc).update(anyString(), any(Object[].class));

        BookingRepository repository = new BookingRepository(jdbc, NO_OP_TRANSACTIONS);

        assertThat(repository.tryInsert(booking()))
                .isInstanceOf(BookingInsertResult.SlotTaken.class);
    }

    @Test
    void returnsSlotTakenFromThePreCheckWithoutInserting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        BookingRepository repository = new BookingRepository(jdbc, NO_OP_TRANSACTIONS);

        assertThat(repository.tryInsert(booking()))
                .isInstanceOf(BookingInsertResult.SlotTaken.class);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void returnsInsertedAfterAStoredBooking() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        BookingRepository repository = new BookingRepository(jdbc, NO_OP_TRANSACTIONS);
        Booking booking = booking();

        BookingInsertResult.Inserted inserted =
                (BookingInsertResult.Inserted) repository.tryInsert(booking);
        assertThat(inserted.booking()).isEqualTo(booking);
    }

    @Test
    void wrapsUnexpectedDriverFailuresSoTheyDoNotLeavePersistence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        doThrow(new UncategorizedSQLException(
                        "insert booking",
                        "INSERT INTO bookings",
                        new SQLiteException("disk I/O error", SQLiteErrorCode.SQLITE_IOERR)))
                .when(jdbc).update(anyString(), any(Object[].class));

        BookingRepository repository = new BookingRepository(jdbc, NO_OP_TRANSACTIONS);

        assertThatThrownBy(() -> repository.tryInsert(booking()))
                .isInstanceOf(BookingPersistenceException.class)
                .hasCauseInstanceOf(UncategorizedSQLException.class);
    }
}
