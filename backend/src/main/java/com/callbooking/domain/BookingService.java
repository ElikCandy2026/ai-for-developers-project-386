package com.callbooking.domain;

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

import com.callbooking.api.ApiTime;
import com.callbooking.api.model.Booking;
import com.callbooking.api.model.CreateBookingRequest;
import com.callbooking.api.model.MeetingType;
import com.callbooking.application.ApplicationErrorCode;
import com.callbooking.application.ApplicationException;
import com.callbooking.config.AppProperties;
import com.callbooking.persistence.BookingRepository;
import com.callbooking.persistence.BookingRepository.BookingRow;

@Service
public class BookingService {

    private final MeetingTypeService meetingTypeService;
    private final BookingRepository bookingRepository;
    private final OwnerService ownerService;
    private final AppProperties properties;
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
            AppProperties properties,
            PlatformTransactionManager transactionManager) {
        this.meetingTypeService = meetingTypeService;
        this.bookingRepository = bookingRepository;
        this.ownerService = ownerService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Booking create(CreateBookingRequest request) {
        bookingLock.lock();
        try {
            return transactionTemplate.execute(status -> createInsideTransaction(request));
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

    private Booking createInsideTransaction(CreateBookingRequest request) {
        // Syntax (presence, lengths, patterns, email) belongs to Bean Validation
        // generated from the contract. This method owns only the scheduling
        // semantics and normalisation.
        String guestName = trim(request.getGuestName());
        String guestEmail = trim(request.getGuestEmail());

        MeetingType meetingType = meetingTypeService.get(request.getMeetingTypeId());
        AppProperties.Owner owner = properties.owner();
        ZoneId zone = ownerService.zone();

        Instant start = request.getStart().toInstant().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime localStart = LocalDateTime.ofInstant(start, zone);
        LocalDate date = localStart.toLocalDate();
        LocalTime time = localStart.toLocalTime();
        int durationMinutes = meetingType.getDurationMinutes();

        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw unavailable();
        }

        LocalDate windowStart = LocalDate.now(zone).plusDays(owner.bookingWindowStartOffsetDays());
        LocalDate windowEnd = windowStart.plusDays(owner.bookingWindowDays() - 1L);
        if (date.isBefore(windowStart) || date.isAfter(windowEnd)) {
            throw unavailable();
        }

        if (!ownerService.isWorkingDay(date.getDayOfWeek())) {
            throw unavailable();
        }

        LocalTime workdayStart = owner.workdayStart();
        LocalTime workdayEnd = owner.workdayEnd();
        if (time.isBefore(workdayStart) || time.plusMinutes(durationMinutes).isAfter(workdayEnd)) {
            throw unavailable();
        }

        long minutesFromWorkdayStart = Duration.between(workdayStart, time).toMinutes();
        if (minutesFromWorkdayStart < 0 || minutesFromWorkdayStart % owner.slotStepMinutes() != 0) {
            throw unavailable();
        }

        Instant threshold = Instant.now().plus(Duration.ofMinutes(owner.minimumNoticeMinutes()));
        if (start.isBefore(threshold)) {
            throw unavailable();
        }

        long startMs = start.toEpochMilli();
        long endMs = start.plus(Duration.ofMinutes(durationMinutes)).toEpochMilli();
        if (bookingRepository.existsOverlap(startMs, endMs)) {
            throw slotTaken();
        }

        String notes = request.getNotes() == null ? null : request.getNotes().trim();
        if (notes != null && notes.isEmpty()) {
            notes = null;
        }

        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Booking booking = new Booking()
                .id(UUID.randomUUID().toString())
                .meetingType(meetingTypeService.summary(meetingType))
                .guestName(guestName)
                .guestEmail(guestEmail)
                .notes(notes)
                .start(ApiTime.utc(start))
                .end(ApiTime.utc(Instant.ofEpochMilli(endMs)))
                .createdAt(ApiTime.utc(createdAt));

        bookingRepository.insert(new BookingRow(
                booking.getId(),
                meetingType.getId(),
                guestName,
                guestEmail,
                notes,
                startMs,
                endMs,
                createdAt.toEpochMilli()));

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
