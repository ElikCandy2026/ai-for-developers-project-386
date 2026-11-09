package com.callbooking.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.callbooking.domain.Booking;
import com.callbooking.domain.MeetingType;
import com.callbooking.domain.SchedulingPolicy;
import com.callbooking.persistence.BookingInsertResult;
import com.callbooking.persistence.BookingRepository;

@Service
public class BookingService {

    private final MeetingTypeService meetingTypeService;
    private final BookingRepository bookingRepository;
    private final SchedulingPolicy policy;

    public BookingService(
            MeetingTypeService meetingTypeService,
            BookingRepository bookingRepository,
            SchedulingPolicy policy) {
        this.meetingTypeService = meetingTypeService;
        this.bookingRepository = bookingRepository;
        this.policy = policy;
    }

    public Booking create(
            String meetingTypeId,
            Instant start,
            String guestName,
            String guestEmail,
            String notes) {
        // Syntax (presence, lengths, patterns, email) belongs to Bean Validation
        // generated from the contract. The scheduling policy owns the calendar
        // semantics; the repository owns the atomic insert and slot conflicts.
        MeetingType meetingType = meetingTypeService.get(meetingTypeId);
        int durationMinutes = meetingType.durationMinutes();

        SchedulingPolicy.SlotCheck check = policy.checkStart(start, durationMinutes, List.of());
        if (!check.ok()) {
            throw unavailable();
        }

        String normalisedNotes = notes == null ? null : notes.trim();
        if (normalisedNotes != null && normalisedNotes.isEmpty()) {
            normalisedNotes = null;
        }

        Booking booking = new Booking(
                UUID.randomUUID().toString(),
                meetingTypeService.summary(meetingType),
                trim(guestName),
                trim(guestEmail),
                normalisedNotes,
                check.start(),
                check.end(),
                policy.now().truncatedTo(ChronoUnit.MILLIS));

        return switch (bookingRepository.tryInsert(booking)) {
            case BookingInsertResult.Inserted inserted -> inserted.booking();
            case BookingInsertResult.SlotTaken ignored -> throw slotTaken();
        };
    }

    public List<Booking> list(boolean includePast) {
        return bookingRepository.findBookings(includePast, policy.now());
    }

    private ApplicationException unavailable() {
        return ApplicationException.of(
                ApplicationErrorCode.SLOT_UNAVAILABLE,
                "Слот недоступен. Выберите свободное время в пределах окна записи.");
    }

    private ApplicationException slotTaken() {
        return ApplicationException.of(
                ApplicationErrorCode.SLOT_TAKEN,
                "Это время уже занято другой встречей. Выберите другой слот.");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
