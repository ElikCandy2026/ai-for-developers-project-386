package com.callbooking.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.callbooking.domain.Availability;
import com.callbooking.domain.AvailabilityDay;
import com.callbooking.domain.BookingInterval;
import com.callbooking.domain.BookingRules;
import com.callbooking.domain.DayOfWeek;
import com.callbooking.domain.MeetingType;
import com.callbooking.domain.Slot;
import com.callbooking.persistence.BookingRepository;

@Service
public class AvailabilityService {

    private final MeetingTypeService meetingTypeService;
    private final BookingRepository bookingRepository;
    private final OwnerService ownerService;

    public AvailabilityService(
            MeetingTypeService meetingTypeService,
            BookingRepository bookingRepository,
            OwnerService ownerService) {
        this.meetingTypeService = meetingTypeService;
        this.bookingRepository = bookingRepository;
        this.ownerService = ownerService;
    }

    public Availability getAvailability(String meetingTypeId) {
        MeetingType meetingType = meetingTypeService.get(meetingTypeId);
        BookingRules rules = ownerService.getOwner().rules();
        ZoneId zone = rules.zone();

        LocalDate today = LocalDate.now(zone);
        LocalDate windowStart = today.plusDays(rules.bookingWindowStartOffsetDays());
        LocalDate windowEnd = windowStart.plusDays(rules.bookingWindowDays() - 1L);

        Instant rangeStart = windowStart.atStartOfDay(zone).toInstant();
        Instant rangeEnd = windowEnd.plusDays(1).atStartOfDay(zone).toInstant();
        List<BookingInterval> intervals = bookingRepository.findIntervals(rangeStart, rangeEnd);

        Instant threshold = Instant.now().plus(Duration.ofMinutes(rules.minimumNoticeMinutes()));
        int duration = meetingType.durationMinutes();

        List<AvailabilityDay> days = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(windowEnd); date = date.plusDays(1)) {
            DayOfWeek weekday = DayOfWeek.from(date.getDayOfWeek());
            List<Slot> slots = new ArrayList<>();
            if (rules.isWorkingDay(weekday)) {
                slots = slotsForDay(date, zone, rules, duration, intervals, threshold);
            }
            days.add(new AvailabilityDay(date, weekday, slots));
        }

        return new Availability(
                meetingTypeService.summary(meetingType),
                rules.timezone(),
                windowStart,
                windowEnd,
                rules.slotStepMinutes(),
                duration,
                days);
    }

    private List<Slot> slotsForDay(
            LocalDate date,
            ZoneId zone,
            BookingRules rules,
            int durationMinutes,
            List<BookingInterval> intervals,
            Instant threshold) {

        LocalTime workdayStart = rules.workdayStart();
        LocalTime workdayEnd = rules.workdayEnd();
        ZonedDateTime cursor = date.atTime(workdayStart).atZone(zone);
        ZonedDateTime endOfWorkday = date.atTime(workdayEnd).atZone(zone);

        List<Slot> slots = new ArrayList<>();
        while (!cursor.plusMinutes(durationMinutes).isAfter(endOfWorkday)) {
            Instant start = cursor.toInstant();
            Instant end = start.plus(Duration.ofMinutes(durationMinutes));
            boolean notTooEarly = !start.isBefore(threshold);
            boolean free = intervals.stream()
                    .noneMatch(interval -> interval.overlaps(start, end));

            if (notTooEarly && free) {
                slots.add(new Slot(start, end));
            }
            cursor = cursor.plusMinutes(rules.slotStepMinutes());
        }
        return slots;
    }
}
