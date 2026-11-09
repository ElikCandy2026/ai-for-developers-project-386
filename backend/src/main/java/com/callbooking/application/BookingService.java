package com.callbooking.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.callbooking.domain.Booking;
import com.callbooking.domain.BookingRules;
import com.callbooking.domain.DayOfWeek;
import com.callbooking.domain.MeetingType;
import com.callbooking.persistence.BookingRepository;

@Service
public class BookingService {

    private final MeetingTypeService meetingTypeService;
    private final BookingRepository bookingRepository;
    private final OwnerService ownerService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Serializes booking creation inside this application. The SQLite trigger is
     * the second line of defence and also protects multiple application instances.
     */
    private final ReentrantLock bookingLock = new ReentrantLock(true);

    public BookingService(
            MeetingTypeService meetingTypeService,
            BookingRepository bookingRepository,
            OwnerService ownerService,
            PlatformTransactionManager transactionManager) {
        this.meetingTypeService = meetingTypeService;
        this.bookingRepository = bookingRepository;
        this.ownerService = ownerService;
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
        return bookingRepository.findBookings(includePast, Instant.now());
    }

    private Booking createInsideTransaction(
            String meetingTypeId,
            Instant requestedStart,
            String guestName,
            String guestEmail,
            String notes) {
        // Syntax (presence, lengths, patterns, email) belongs to Bean Validation
        // generated from the contract. This method owns only the scheduling
        // semantics and normalisation.
        String normalisedGuestName = trim(guestName);
        String normalisedGuestEmail = trim(guestEmail);

        MeetingType meetingType = meetingTypeService.get(meetingTypeId);
        BookingRules rules = ownerService.getOwner().rules();
        ZoneId zone = rules.zone();

        Instant start = requestedStart.truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime localStart = LocalDateTime.ofInstant(start, zone);
        LocalDate date = localStart.toLocalDate();
        LocalTime time = localStart.toLocalTime();
        int durationMinutes = meetingType.durationMinutes();

        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw unavailable();
        }

        LocalDate windowStart = LocalDate.now(zone).plusDays(rules.bookingWindowStartOffsetDays());
        LocalDate windowEnd = windowStart.plusDays(rules.bookingWindowDays() - 1L);
        if (date.isBefore(windowStart) || date.isAfter(windowEnd)) {
            throw unavailable();
        }

        if (!rules.isWorkingDay(DayOfWeek.from(date.getDayOfWeek()))) {
            throw unavailable();
        }

        LocalTime workdayStart = rules.workdayStart();
        LocalTime workdayEnd = rules.workdayEnd();
        if (time.isBefore(workdayStart) || time.plusMinutes(durationMinutes).isAfter(workdayEnd)) {
            throw unavailable();
        }

        long minutesFromWorkdayStart = Duration.between(workdayStart, time).toMinutes();
        if (minutesFromWorkdayStart < 0 || minutesFromWorkdayStart % rules.slotStepMinutes() != 0) {
            throw unavailable();
        }

        Instant threshold = Instant.now().plus(Duration.ofMinutes(rules.minimumNoticeMinutes()));
        if (start.isBefore(threshold)) {
            throw unavailable();
        }

        Instant end = start.plus(Duration.ofMinutes(durationMinutes));
        if (bookingRepository.existsOverlap(start, end)) {
            throw slotTaken();
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
                start,
                end,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

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
