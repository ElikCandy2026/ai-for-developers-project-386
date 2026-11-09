package com.callbooking.api;

import java.util.List;

import com.callbooking.api.model.Availability;
import com.callbooking.api.model.AvailabilityDay;
import com.callbooking.api.model.Booking;
import com.callbooking.api.model.BookingRules;
import com.callbooking.api.model.DayOfWeek;
import com.callbooking.api.model.MeetingType;
import com.callbooking.api.model.MeetingTypeSummary;
import com.callbooking.api.model.Owner;
import com.callbooking.api.model.Slot;

/**
 * Maps domain types to the models generated from the TypeSpec contract. The
 * generated DTOs do not leave the api layer, and the domain does not know them.
 */
public final class DomainApiMapper {

    private DomainApiMapper() {
    }

    public static Owner toApi(com.callbooking.domain.Owner owner) {
        return new Owner()
                .id(owner.id())
                .displayName(owner.displayName())
                .email(owner.email())
                .rules(toApi(owner.schedule()));
    }

    public static BookingRules toApi(com.callbooking.domain.OwnerSchedule schedule) {
        return new BookingRules()
                .timezone(schedule.timezone())
                .workingDays(schedule.workingDays().stream()
                        .map(DomainApiMapper::toApiDayOfWeek)
                        .toList())
                .workdayStart(schedule.workdayStart().toString())
                .workdayEnd(schedule.workdayEnd().toString())
                .slotStepMinutes(schedule.slotStepMinutes())
                .bookingWindowDays(schedule.bookingWindowDays())
                .bookingWindowStartOffsetDays(schedule.bookingWindowStartOffsetDays())
                .minimumNoticeMinutes(schedule.minimumNoticeMinutes())
                .minimumDurationMinutes(schedule.minimumDurationMinutes())
                .maximumDurationMinutes(schedule.maximumDurationMinutes());
    }

    public static MeetingType toApi(com.callbooking.domain.MeetingType meetingType) {
        return new MeetingType()
                .id(meetingType.id())
                .name(meetingType.name())
                .description(meetingType.description())
                .durationMinutes(meetingType.durationMinutes())
                .createdAt(ApiTime.utc(meetingType.createdAt()));
    }

    public static MeetingTypeSummary toApi(com.callbooking.domain.MeetingTypeSummary summary) {
        return new MeetingTypeSummary()
                .id(summary.id())
                .name(summary.name())
                .description(summary.description())
                .durationMinutes(summary.durationMinutes());
    }

    public static Booking toApi(com.callbooking.domain.Booking booking) {
        return new Booking()
                .id(booking.id())
                .meetingType(toApi(booking.meetingType()))
                .guestName(booking.guestName())
                .guestEmail(booking.guestEmail())
                .notes(booking.notes())
                .start(ApiTime.utc(booking.start()))
                .end(ApiTime.utc(booking.end()))
                .createdAt(ApiTime.utc(booking.createdAt()));
    }

    public static Availability toApi(com.callbooking.domain.Availability availability) {
        return new Availability()
                .meetingType(toApi(availability.meetingType()))
                .timezone(availability.timezone())
                .windowStartDate(availability.windowStartDate())
                .windowEndDate(availability.windowEndDate())
                .slotStepMinutes(availability.slotStepMinutes())
                .durationMinutes(availability.durationMinutes())
                .days(availability.days().stream()
                        .map(DomainApiMapper::toApi)
                        .toList());
    }

    public static AvailabilityDay toApi(com.callbooking.domain.AvailabilityDay day) {
        List<Slot> slots = day.slots().stream()
                .map(DomainApiMapper::toApi)
                .toList();
        return new AvailabilityDay()
                .date(day.date())
                .weekday(toApiDayOfWeek(day.weekday()))
                .slots(slots);
    }

    public static Slot toApi(com.callbooking.domain.Slot slot) {
        return new Slot()
                .start(ApiTime.utc(slot.start()))
                .end(ApiTime.utc(slot.end()));
    }

    public static List<MeetingType> toApi(List<com.callbooking.domain.MeetingType> meetingTypes) {
        return meetingTypes.stream()
                .map(DomainApiMapper::toApi)
                .toList();
    }

    public static List<Booking> bookingsToApi(List<com.callbooking.domain.Booking> bookings) {
        return bookings.stream()
                .map(DomainApiMapper::toApi)
                .toList();
    }

    private static DayOfWeek toApiDayOfWeek(com.callbooking.domain.DayOfWeek day) {
        return DayOfWeek.valueOf(day.name());
    }
}
