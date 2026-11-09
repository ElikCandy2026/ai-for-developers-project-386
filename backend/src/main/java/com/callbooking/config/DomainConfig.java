package com.callbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.callbooking.domain.BookingRules;
import com.callbooking.domain.DayOfWeek;
import com.callbooking.domain.Owner;

/**
 * Builds the domain owner snapshot from configuration. Application use cases
 * receive domain types, so they do not depend on Spring configuration classes.
 */
@Configuration
public class DomainConfig {

    @Bean
    public Owner owner(AppProperties properties) {
        AppProperties.Owner config = properties.owner();
        BookingRules rules = new BookingRules(
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
        return new Owner(config.id(), config.displayName(), config.email(), rules);
    }
}
