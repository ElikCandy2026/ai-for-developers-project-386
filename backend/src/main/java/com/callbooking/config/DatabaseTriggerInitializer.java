package com.callbooking.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The overlap trigger lives in a separate statement because Spring's SQL script
 * parser splits statements on semicolons and cannot parse a trigger body.
 */
@Component
@DependsOn("dataSourceScriptDatabaseInitializer")
public class DatabaseTriggerInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseTriggerInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterPropertiesSet() {
        jdbcTemplate.execute("""
                CREATE TRIGGER IF NOT EXISTS trg_bookings_prevent_overlap
                BEFORE INSERT ON bookings
                FOR EACH ROW
                WHEN EXISTS (
                    SELECT 1
                    FROM bookings b
                    WHERE NEW.start_ms < b.end_ms
                      AND NEW.end_ms > b.start_ms
                )
                BEGIN
                    SELECT RAISE(ABORT, 'booking_overlap');
                END
                """);
    }
}
