package com.callbooking.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.callbooking.domain.Availability;
import com.callbooking.domain.AvailabilityDay;
import com.callbooking.domain.BookingInterval;
import com.callbooking.domain.DayOfWeek;
import com.callbooking.domain.MeetingType;
import com.callbooking.domain.OwnerSchedule;
import com.callbooking.domain.SchedulingPolicy;
import com.callbooking.domain.Slot;
import com.callbooking.persistence.BookingRepository;

@Service
public class AvailabilityService {

    private final MeetingTypeService meetingTypeService;
    private final BookingRepository bookingRepository;
    private final SchedulingPolicy policy;

    public AvailabilityService(
            MeetingTypeService meetingTypeService,
            BookingRepository bookingRepository,
            SchedulingPolicy policy) {
        this.meetingTypeService = meetingTypeService;
        this.bookingRepository = bookingRepository;
        this.policy = policy;
    }

    public Availability getAvailability(String meetingTypeId) {
        MeetingType meetingType = meetingTypeService.get(meetingTypeId);
        OwnerSchedule schedule = policy.schedule();
        SchedulingPolicy.Window window = policy.window();
        List<BookingInterval> intervals = bookingRepository.findIntervals(
                window.rangeStart(), window.rangeEnd());
        int duration = meetingType.durationMinutes();

        List<AvailabilityDay> days = new ArrayList<>();
        for (var date = window.startDate();
                !date.isAfter(window.endDate());
                date = date.plusDays(1)) {
            DayOfWeek weekday = DayOfWeek.from(date.getDayOfWeek());
            List<Slot> slots = policy.slotsOn(date, duration, intervals);
            days.add(new AvailabilityDay(date, weekday, slots));
        }

        return new Availability(
                meetingTypeService.summary(meetingType),
                schedule.timezone(),
                window.startDate(),
                window.endDate(),
                schedule.slotStepMinutes(),
                duration,
                days);
    }
}
