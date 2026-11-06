package com.callbooking.domain;

import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.callbooking.api.model.BookingRules;
import com.callbooking.api.model.DayOfWeek;
import com.callbooking.api.model.Owner;
import com.callbooking.config.AppProperties;

@Service
public class OwnerService {

    private final AppProperties properties;

    public OwnerService(AppProperties properties) {
        this.properties = properties;
    }

    public Owner getOwner() {
        AppProperties.Owner owner = properties.owner();
        return new Owner()
                .id(owner.id())
                .displayName(owner.displayName())
                .email(owner.email())
                .rules(getRules());
    }

    public BookingRules getRules() {
        AppProperties.Owner owner = properties.owner();
        return new BookingRules()
                .timezone(owner.timezone())
                .workingDays(owner.workingDays().stream()
                        .map(day -> DayOfWeek.valueOf(day.name()))
                        .toList())
                .workdayStart(owner.workdayStart().toString())
                .workdayEnd(owner.workdayEnd().toString())
                .slotStepMinutes(owner.slotStepMinutes())
                .bookingWindowDays(owner.bookingWindowDays())
                .bookingWindowStartOffsetDays(owner.bookingWindowStartOffsetDays())
                .minimumNoticeMinutes(owner.minimumNoticeMinutes())
                .minimumDurationMinutes(owner.minimumDurationMinutes())
                .maximumDurationMinutes(owner.maximumDurationMinutes());
    }

    public AppProperties.Owner config() {
        return properties.owner();
    }

    public ZoneId zone() {
        return ZoneId.of(properties.owner().timezone());
    }

    public List<java.time.DayOfWeek> workingDays() {
        return properties.owner().workingDays();
    }

    public boolean isWorkingDay(java.time.DayOfWeek day) {
        return properties.owner().workingDays().contains(day);
    }
}
