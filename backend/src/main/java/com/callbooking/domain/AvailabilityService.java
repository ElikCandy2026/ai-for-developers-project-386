package com.callbooking.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.callbooking.api.ApiTime;
import com.callbooking.api.model.Availability;
import com.callbooking.api.model.AvailabilityDay;
import com.callbooking.api.model.MeetingType;
import com.callbooking.api.model.Slot;
import com.callbooking.config.AppProperties;
import com.callbooking.persistence.BookingRepository;
import com.callbooking.persistence.BookingRepository.BookingInterval;

@Service
public class AvailabilityService {

    private final MeetingTypeService meetingTypeService;
    private final BookingRepository bookingRepository;
    private final OwnerService ownerService;
    private final AppProperties properties;

    public AvailabilityService(
            MeetingTypeService meetingTypeService,
            BookingRepository bookingRepository,
            OwnerService ownerService,
            AppProperties properties) {
        this.meetingTypeService = meetingTypeService;
        this.bookingRepository = bookingRepository;
        this.ownerService = ownerService;
        this.properties = properties;
    }

    public Availability getAvailability(String meetingTypeId) {
        MeetingType meetingType = meetingTypeService.get(meetingTypeId);
        AppProperties.Owner owner = properties.owner();
        ZoneId zone = ownerService.zone();

        LocalDate today = LocalDate.now(zone);
        LocalDate windowStart = today.plusDays(owner.bookingWindowStartOffsetDays());
        LocalDate windowEnd = windowStart.plusDays(owner.bookingWindowDays() - 1L);

        Instant rangeStart = windowStart.atStartOfDay(zone).toInstant();
        Instant rangeEnd = windowEnd.plusDays(1).atStartOfDay(zone).toInstant();
        List<BookingInterval> intervals = bookingRepository.findIntervals(
                rangeStart.toEpochMilli(),
                rangeEnd.toEpochMilli());

        Instant threshold = Instant.now().plus(Duration.ofMinutes(owner.minimumNoticeMinutes()));
        int duration = meetingType.getDurationMinutes();

        List<AvailabilityDay> days = new ArrayList<>();
        for (LocalDate date = windowStart; !date.isAfter(windowEnd); date = date.plusDays(1)) {
            List<Slot> slots = new ArrayList<>();
            if (ownerService.isWorkingDay(date.getDayOfWeek())) {
                slots = slotsForDay(
                        date,
                        zone,
                        owner,
                        duration,
                        intervals,
                        threshold);
            }
            days.add(new AvailabilityDay()
                    .date(date)
                    .weekday(com.callbooking.api.model.DayOfWeek.valueOf(date.getDayOfWeek().name()))
                    .slots(slots));
        }

        return new Availability()
                .meetingType(meetingTypeService.summary(meetingType))
                .timezone(owner.timezone())
                .windowStartDate(windowStart)
                .windowEndDate(windowEnd)
                .slotStepMinutes(owner.slotStepMinutes())
                .durationMinutes(duration)
                .days(days);
    }

    private List<Slot> slotsForDay(
            LocalDate date,
            ZoneId zone,
            AppProperties.Owner owner,
            int durationMinutes,
            List<BookingInterval> intervals,
            Instant threshold) {

        LocalTime workdayStart = owner.workdayStart();
        LocalTime workdayEnd = owner.workdayEnd();
        ZonedDateTime cursor = date.atTime(workdayStart).atZone(zone);
        ZonedDateTime endOfWorkday = date.atTime(workdayEnd).atZone(zone);

        List<Slot> slots = new ArrayList<>();
        while (!cursor.plusMinutes(durationMinutes).isAfter(endOfWorkday)) {
            Instant start = cursor.toInstant();
            Instant end = start.plus(Duration.ofMinutes(durationMinutes));
            boolean notTooEarly = !start.isBefore(threshold);
            boolean free = intervals.stream()
                    .noneMatch(interval -> interval.overlaps(start.toEpochMilli(), end.toEpochMilli()));

            if (notTooEarly && free) {
                slots.add(new Slot()
                        .start(ApiTime.utc(start))
                        .end(ApiTime.utc(end)));
            }
            cursor = cursor.plusMinutes(owner.slotStepMinutes());
        }
        return slots;
    }
}
