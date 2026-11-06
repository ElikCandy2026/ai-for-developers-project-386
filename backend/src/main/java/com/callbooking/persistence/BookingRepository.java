package com.callbooking.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.callbooking.api.ApiTime;
import com.callbooking.api.model.Booking;
import com.callbooking.api.model.MeetingTypeSummary;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(BookingRow row) {
        jdbcTemplate.update("""
                        INSERT INTO bookings
                            (id, meeting_type_id, guest_name, guest_email, notes,
                             start_ms, end_ms, created_at_ms)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                row.id(),
                row.meetingTypeId(),
                row.guestName(),
                row.guestEmail(),
                row.notes(),
                row.startMs(),
                row.endMs(),
                row.createdAtMs());
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

    public List<BookingInterval> findIntervals(long rangeStartMs, long rangeEndMs) {
        return jdbcTemplate.query("""
                        SELECT start_ms, end_ms
                        FROM bookings
                        WHERE start_ms < ? AND end_ms > ?
                        """,
                (rs, rowNum) -> new BookingInterval(rs.getLong("start_ms"), rs.getLong("end_ms")),
                rangeEndMs,
                rangeStartMs);
    }

    public boolean existsOverlap(long startMs, long endMs) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM bookings
                WHERE start_ms < ? AND end_ms > ?
                """, Integer.class, endMs, startMs);
        return count != null && count > 0;
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM bookings");
    }

    private Booking mapBooking(ResultSet rs, int rowNum) throws SQLException {
        MeetingTypeSummary meetingType = new MeetingTypeSummary()
                .id(rs.getString("mt_id"))
                .name(rs.getString("mt_name"))
                .description(rs.getString("mt_description"))
                .durationMinutes(rs.getInt("mt_duration"));

        return new Booking()
                .id(rs.getString("id"))
                .meetingType(meetingType)
                .guestName(rs.getString("guest_name"))
                .guestEmail(rs.getString("guest_email"))
                .notes(rs.getString("notes"))
                .start(ApiTime.utc(Instant.ofEpochMilli(rs.getLong("start_ms"))))
                .end(ApiTime.utc(Instant.ofEpochMilli(rs.getLong("end_ms"))))
                .createdAt(ApiTime.utc(Instant.ofEpochMilli(rs.getLong("created_at_ms"))));
    }

    public record BookingRow(
            String id,
            String meetingTypeId,
            String guestName,
            String guestEmail,
            String notes,
            long startMs,
            long endMs,
            long createdAtMs) {
    }

    public record BookingInterval(long startMs, long endMs) {

        public boolean overlaps(long otherStartMs, long otherEndMs) {
            return otherStartMs < endMs && otherEndMs > startMs;
        }
    }
}
