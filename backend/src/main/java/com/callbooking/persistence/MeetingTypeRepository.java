package com.callbooking.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.callbooking.api.ApiTime;
import com.callbooking.api.model.MeetingType;

@Repository
public class MeetingTypeRepository {

    private final JdbcTemplate jdbcTemplate;

    public MeetingTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MeetingType> findAll() {
        return jdbcTemplate.query("""
                SELECT id, name, description, duration_minutes, created_at_ms
                FROM meeting_types
                ORDER BY created_at_ms, id
                """, this::map);
    }

    public Optional<MeetingType> findById(String id) {
        return jdbcTemplate.query("""
                        SELECT id, name, description, duration_minutes, created_at_ms
                        FROM meeting_types
                        WHERE id = ?
                        """, this::map, id)
                .stream()
                .findFirst();
    }

    public void insert(MeetingType meetingType) {
        jdbcTemplate.update("""
                        INSERT INTO meeting_types (id, name, description, duration_minutes, created_at_ms)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                meetingType.getId(),
                meetingType.getName(),
                meetingType.getDescription(),
                meetingType.getDurationMinutes(),
                ApiTime.instant(meetingType.getCreatedAt()).toEpochMilli());
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM meeting_types", Integer.class);
        return count == null ? 0 : count;
    }

    private MeetingType map(ResultSet rs, int rowNum) throws SQLException {
        return new MeetingType()
                .id(rs.getString("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .durationMinutes(rs.getInt("duration_minutes"))
                .createdAt(ApiTime.utc(java.time.Instant.ofEpochMilli(rs.getLong("created_at_ms"))));
    }
}
