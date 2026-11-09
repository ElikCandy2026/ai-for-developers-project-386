package com.callbooking.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.callbooking.domain.DayOfWeek;
import com.callbooking.domain.Owner;
import com.callbooking.domain.OwnerSchedule;
import com.callbooking.domain.SchedulingPolicy;

/**
 * Builds the domain owner snapshot and the scheduling policy from configuration.
 * Application use cases receive domain types, so they do not depend on Spring
 * configuration classes.
 */
@Configuration
public class DomainConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public Owner owner(AppProperties properties) {
        AppProperties.Owner config = properties.owner();
        OwnerSchedule schedule = new OwnerSchedule(
                config.timezone(),
                config.workingDays().stream()
                        .map(DayOfWeek::from)
                        .toList(),
                config.workdayStart(),
                config.workdayEnd(),
                config.slotStepMinutes(),
                config.bookingWindowDays(),
                config.bookingWindowStartOffsetDays(),
                config.minimumNoticeMinutes(),
                config.minimumDurationMinutes(),
                config.maximumDurationMinutes());
        return new Owner(config.id(), config.displayName(), config.email(), schedule);
    }

    @Bean
    public SchedulingPolicy schedulingPolicy(Owner owner, Clock clock) {
        return new SchedulingPolicy(owner.schedule(), clock);
    }
}
