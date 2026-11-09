package com.callbooking.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for calendar semantics. Availability and booking both
 * ask the same policy about the window, working days and hours, the slot grid,
 * minimum notice and overlap. Time comes from an injected {@link Clock}, so a
 * fixed instant makes every boundary deterministic.
 *
 * <p>The policy returns values and small result types, never transport errors;
 * the application layer maps them to error codes.</p>
 */
public final class SchedulingPolicy {

    /** Meeting durations must be a multiple of this alignment in minutes. */
    public static final int DURATION_ALIGNMENT_MINUTES = 15;

    public enum DurationAlignment {
        OK,
        NOT_ALIGNED
    }

    public enum SlotStatus {
        OK,
        OUTSIDE_WINDOW,
        NON_WORKING_DAY,
        OUTSIDE_WORKING_HOURS,
        OFF_GRID,
        TOO_SOON,
        TAKEN
    }

    public record Window(
            LocalDate startDate,
            LocalDate endDate,
            Instant rangeStart,
            Instant rangeEnd,
            Instant noticeThreshold) {
    }

    public record SlotCheck(SlotStatus status, Instant start, Instant end) {

        public boolean ok() {
            return status == SlotStatus.OK;
        }
    }

    private final OwnerSchedule schedule;
    private final Clock clock;

    public SchedulingPolicy(OwnerSchedule schedule, Clock clock) {
        this.schedule = schedule;
        this.clock = clock;
    }

    public OwnerSchedule schedule() {
        return schedule;
    }

    public Clock clock() {
        return clock;
    }

    /** Current instant, used for timestamps and "upcoming" filters. */
    public Instant now() {
        return clock.instant();
    }

    /** Today's date in the owner's zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(schedule.zone()));
    }

    public Window window() {
        LocalDate startDate = today().plusDays(schedule.bookingWindowStartOffsetDays());
        LocalDate endDate = startDate.plusDays(schedule.bookingWindowDays() - 1L);
        ZoneId zone = schedule.zone();
        return new Window(
                startDate,
                endDate,
                startDate.atStartOfDay(zone).toInstant(),
                endDate.plusDays(1).atStartOfDay(zone).toInstant(),
                now().plus(Duration.ofMinutes(schedule.minimumNoticeMinutes())));
    }

    public boolean isWorkingDay(LocalDate date) {
        return schedule.isWorkingDay(DayOfWeek.from(date.getDayOfWeek()));
    }

    public DurationAlignment durationAlignment(int durationMinutes) {
        return durationMinutes > 0 && durationMinutes % DURATION_ALIGNMENT_MINUTES == 0
                ? DurationAlignment.OK
                : DurationAlignment.NOT_ALIGNED;
    }

    /**
     * Free slots for one owner-local day, in grid order. Non-working days return
     * an empty list, and occupied intervals remove every slot they touch.
     */
    public List<Slot> slotsOn(LocalDate date, int durationMinutes, List<BookingInterval> occupied) {
        if (!isWorkingDay(date)) {
            return List.of();
        }

        ZoneId zone = schedule.zone();
        LocalTime workdayEnd = schedule.workdayEnd();
        Instant threshold = window().noticeThreshold();
        List<Slot> slots = new ArrayList<>();

        for (LocalTime time = schedule.workdayStart();
                !time.plusMinutes(durationMinutes).isAfter(workdayEnd);
                time = time.plusMinutes(schedule.slotStepMinutes())) {
            Instant start = date.atTime(time).atZone(zone).toInstant();
            Instant end = start.plus(Duration.ofMinutes(durationMinutes));
            if (!start.isBefore(threshold) && !overlaps(occupied, start, end)) {
                slots.add(new Slot(start, end));
            }
        }
        return slots;
    }

    /**
     * Validates a requested start against the same rules that generate slots.
     * The reason is kept for unit tests and diagnostics; the application maps
     * every non-{@code TAKEN} failure to the public {@code SLOT_UNAVAILABLE}.
     */
    public SlotCheck checkStart(
            Instant requestedStart,
            int durationMinutes,
            List<BookingInterval> occupied) {
        Instant start = requestedStart.truncatedTo(ChronoUnit.MILLIS);
        Instant end = start.plus(Duration.ofMinutes(durationMinutes));
        LocalDateTime localStart = LocalDateTime.ofInstant(start, schedule.zone());
        LocalDate date = localStart.toLocalDate();
        LocalTime time = localStart.toLocalTime();

        if (time.getSecond() != 0 || time.getNano() != 0) {
            return new SlotCheck(SlotStatus.OFF_GRID, start, end);
        }

        Window window = window();
        if (date.isBefore(window.startDate()) || date.isAfter(window.endDate())) {
            return new SlotCheck(SlotStatus.OUTSIDE_WINDOW, start, end);
        }
        if (!isWorkingDay(date)) {
            return new SlotCheck(SlotStatus.NON_WORKING_DAY, start, end);
        }

        LocalTime workdayStart = schedule.workdayStart();
        if (time.isBefore(workdayStart)
                || time.plusMinutes(durationMinutes).isAfter(schedule.workdayEnd())) {
            return new SlotCheck(SlotStatus.OUTSIDE_WORKING_HOURS, start, end);
        }

        long minutesFromWorkdayStart = Duration.between(workdayStart, time).toMinutes();
        if (minutesFromWorkdayStart % schedule.slotStepMinutes() != 0) {
            return new SlotCheck(SlotStatus.OFF_GRID, start, end);
        }

        if (start.isBefore(window.noticeThreshold())) {
            return new SlotCheck(SlotStatus.TOO_SOON, start, end);
        }
        if (overlaps(occupied, start, end)) {
            return new SlotCheck(SlotStatus.TAKEN, start, end);
        }
        return new SlotCheck(SlotStatus.OK, start, end);
    }

    /** Adjacent intervals do not overlap: the end of one may equal the start of another. */
    public boolean overlaps(List<BookingInterval> occupied, Instant start, Instant end) {
        return occupied.stream().anyMatch(interval -> interval.overlaps(start, end));
    }
}
