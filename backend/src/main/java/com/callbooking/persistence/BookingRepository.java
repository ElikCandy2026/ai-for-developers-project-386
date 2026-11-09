package com.callbooking.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import com.callbooking.domain.Booking;
import com.callbooking.domain.BookingInterval;
import com.callbooking.domain.MeetingTypeSummary;

/**
 * Deep persistence module for bookings. Creation goes through
 * {@link #tryInsert(Booking)}, which owns the fair lock, the transaction, the
 * interval pre-check and the mapping of the SQLite overlap trigger. Callers never
 * see a driver exception and never repeat the overlap rule.
 */
@Repository
public class BookingRepository {

    private static final int SQLITE_CONSTRAINT_TRIGGER_CODE = 1811;
    private static final String OVERLAP_TRIGGER_MESSAGE = "booking_overlap";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * Serializes insertions inside this application. The SQLite trigger remains
     * the second line of defence and also protects other application instances.
     */
    private final ReentrantLock insertLock = new ReentrantLock(true);

    public BookingRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * The only way to create a booking. Returns {@code Inserted} after a successful
     * insert and {@code SlotTaken} when the interval is occupied, whether the
     * pre-check or the trigger noticed it.
     */
    public BookingInsertResult tryInsert(Booking booking) {
        insertLock.lock();
        try {
            return transactionTemplate.execute(status -> insertInsideTransaction(booking));
        } catch (DataAccessException exception) {
            if (isTriggerConflict(exception)) {
                return new BookingInsertResult.SlotTaken();
            }
            throw new BookingPersistenceException("Failed to insert booking", exception);
        } finally {
            insertLock.unlock();
        }
    }

    public List<Booking> findBookings(boolean includePast, Instant now) {
        String where = includePast ? "" : "WHERE b.start_ms > ?";
        Object[] args = includePast ? new Object[0] : new Object[]{now.toEpochMilli()};
        return jdbcTemplate.query("""
                SELECT b.id, b.guest_name, b.guest_email, b.notes,
                       b.start_ms, b.end_ms, b.created_at_ms,
                       mt.id AS mt_id, mt.name AS mt_name,
                       mt.description AS mt_description, mt.duration_minutes AS mt_duration
                FROM bookings b
                JOIN meeting_types mt ON mt.id = b.meeting_type_id
                %s
                ORDER BY b.start_ms, b.id
                """.formatted(where), this::mapBooking, args);
    }

    public List<BookingInterval> findIntervals(Instant rangeStart, Instant rangeEnd) {
        return jdbcTemplate.query("""
                        SELECT start_ms, end_ms
                        FROM bookings
                        WHERE start_ms < ? AND end_ms > ?
                        """,
                (rs, rowNum) -> new BookingInterval(
                        Instant.ofEpochMilli(rs.getLong("start_ms")),
                        Instant.ofEpochMilli(rs.getLong("end_ms"))),
                rangeEnd.toEpochMilli(),
                rangeStart.toEpochMilli());
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM bookings");
    }

    private BookingInsertResult insertInsideTransaction(Booking booking) {
        if (hasOverlap(booking.start(), booking.end())) {
            return new BookingInsertResult.SlotTaken();
        }
        jdbcTemplate.update("""
                        INSERT INTO bookings
                            (id, meeting_type_id, guest_name, guest_email, notes,
                             start_ms, end_ms, created_at_ms)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                booking.id(),
                booking.meetingType().id(),
                booking.guestName(),
                booking.guestEmail(),
                booking.notes(),
                booking.start().toEpochMilli(),
                booking.end().toEpochMilli(),
                booking.createdAt().toEpochMilli());
        return new BookingInsertResult.Inserted(booking);
    }

    private boolean hasOverlap(Instant start, Instant end) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM bookings
                WHERE start_ms < ? AND end_ms > ?
                """, Integer.class, end.toEpochMilli(), start.toEpochMilli());
        return count != null && count > 0;
    }

    /**
     * The trigger aborts with {@code SQLITE_CONSTRAINT_TRIGGER} (1811) and the
     * message {@code booking_overlap}; both signals are accepted so the mapping
     * survives driver or SQLite wording changes.
     */
    static boolean isTriggerConflict(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLiteException sqlite
                    && sqlite.getResultCode() != null
                    && sqlite.getResultCode().code == SQLITE_CONSTRAINT_TRIGGER_CODE) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains(OVERLAP_TRIGGER_MESSAGE)) {
                return true;
            }
        }
        return false;
    }

    private Booking mapBooking(ResultSet rs, int rowNum) throws SQLException {
        MeetingTypeSummary meetingType = new MeetingTypeSummary(
                rs.getString("mt_id"),
                rs.getString("mt_name"),
                rs.getString("mt_description"),
                rs.getInt("mt_duration"));

        return new Booking(
                rs.getString("id"),
                meetingType,
                rs.getString("guest_name"),
                rs.getString("guest_email"),
                rs.getString("notes"),
                Instant.ofEpochMilli(rs.getLong("start_ms")),
                Instant.ofEpochMilli(rs.getLong("end_ms")),
                Instant.ofEpochMilli(rs.getLong("created_at_ms")));
    }
}
