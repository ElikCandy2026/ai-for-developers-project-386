package com.callbooking.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.callbooking.domain.Booking;
import com.callbooking.domain.BookingInterval;
import com.callbooking.domain.MeetingType;
import com.callbooking.domain.SchedulingPolicy;
import com.callbooking.persistence.BookingRepository;

@Service
public class BookingService {

    private final MeetingTypeService meetingTypeService;
    private final BookingRepository bookingRepository;
    private final SchedulingPolicy policy;
    private final TransactionTemplate transactionTemplate;

    /**
     * Serializes booking creation inside this application. The SQLite trigger is
     * the second line of defence and also protects multiple application instances.
     */
    private final ReentrantLock bookingLock = new ReentrantLock(true);

    public BookingService(
            MeetingTypeService meetingTypeService,
            BookingRepository bookingRepository,
            SchedulingPolicy policy,
            PlatformTransactionManager transactionManager) {
        this.meetingTypeService = meetingTypeService;
        this.bookingRepository = bookingRepository;
        this.policy = policy;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Booking create(
            String meetingTypeId,
            Instant start,
            String guestName,
            String guestEmail,
            String notes) {
        bookingLock.lock();
        try {
            return transactionTemplate.execute(status -> createInsideTransaction(
                    meetingTypeId, start, guestName, guestEmail, notes));
        } catch (DataAccessException exception) {
            if (isOverlapConflict(exception)) {
                throw slotTaken();
            }
            throw exception;
        } finally {
            bookingLock.unlock();
        }
    }

    public List<Booking> list(boolean includePast) {
        return bookingRepository.findBookings(includePast, policy.now());
    }

    private Booking createInsideTransaction(
            String meetingTypeId,
            Instant requestedStart,
            String guestName,
            String guestEmail,
            String notes) {
        // Syntax (presence, lengths, patterns, email) belongs to Bean Validation
        // generated from the contract. The scheduling policy owns the calendar
        // semantics; this method only normalises and maps its result to an error.
        String normalisedGuestName = trim(guestName);
        String normalisedGuestEmail = trim(guestEmail);

        MeetingType meetingType = meetingTypeService.get(meetingTypeId);
        int durationMinutes = meetingType.durationMinutes();
        Instant start = requestedStart.truncatedTo(ChronoUnit.MILLIS);
        Instant end = start.plus(Duration.ofMinutes(durationMinutes));

        List<BookingInterval> occupied = bookingRepository.findIntervals(start, end);
        SchedulingPolicy.SlotCheck check = policy.checkStart(start, durationMinutes, occupied);
        if (check.status() == SchedulingPolicy.SlotStatus.TAKEN) {
            throw slotTaken();
        }
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
                normalisedGuestName,
                normalisedGuestEmail,
                normalisedNotes,
                check.start(),
                check.end(),
                policy.now().truncatedTo(ChronoUnit.MILLIS));

        bookingRepository.insert(booking);
        return booking;
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

    private boolean isOverlapConflict(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("booking_overlap")) {
                return true;
            }
        }
        return false;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
