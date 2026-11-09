package com.callbooking.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.callbooking.domain.Booking;
import com.callbooking.domain.BookingInterval;
import com.callbooking.domain.MeetingTypeSummary;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Booking booking) {
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
