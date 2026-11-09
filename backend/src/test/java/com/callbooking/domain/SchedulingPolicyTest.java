package com.callbooking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class SchedulingPolicyTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Instant MONDAY_MORNING = Instant.parse("2026-11-09T06:00:00Z");

    private static final OwnerSchedule SCHEDULE = new OwnerSchedule(
            "Europe/Moscow",
            List.of(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY),
            LocalTime.of(10, 0),
            LocalTime.of(19, 0),
            30,
            14,
            0,
            0,
            15,
            240);

    private SchedulingPolicy policy() {
        return new SchedulingPolicy(SCHEDULE, Clock.fixed(MONDAY_MORNING, ZONE));
    }

    private SchedulingPolicy policy(OwnerSchedule schedule, Instant now) {
        return new SchedulingPolicy(schedule, Clock.fixed(now, ZONE));
    }

    private OwnerSchedule scheduleWith(int startOffsetDays, int noticeMinutes) {
        return new OwnerSchedule(
                SCHEDULE.timezone(),
                SCHEDULE.workingDays(),
                SCHEDULE.workdayStart(),
                SCHEDULE.workdayEnd(),
                SCHEDULE.slotStepMinutes(),
                SCHEDULE.bookingWindowDays(),
                startOffsetDays,
                noticeMinutes,
                SCHEDULE.minimumDurationMinutes(),
                SCHEDULE.maximumDurationMinutes());
    }

    private static Instant msk(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    @Test
    void windowCoversFourteenOwnerLocalDays() {
        SchedulingPolicy.Window window = policy().window();

        assertThat(window.startDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(window.endDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(window.rangeStart()).isEqualTo(Instant.parse("2026-11-08T21:00:00Z"));
        assertThat(window.rangeEnd()).isEqualTo(Instant.parse("2026-11-22T21:00:00Z"));
        assertThat(window.noticeThreshold()).isEqualTo(MONDAY_MORNING);
    }

    @Test
    void windowHonoursStartOffsetAndMinimumNotice() {
        SchedulingPolicy policy = policy(scheduleWith(1, 60), MONDAY_MORNING);
        SchedulingPolicy.Window window = policy.window();

        assertThat(window.startDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(window.endDate()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(window.noticeThreshold()).isEqualTo(MONDAY_MORNING.plus(Duration.ofMinutes(60)));
    }

    @Test
    void todayUsesTheOwnerZone() {
        SchedulingPolicy lateEvening = policy(SCHEDULE, Instant.parse("2026-11-09T22:00:00Z"));

        assertThat(lateEvening.today()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void nonWorkingDaysHaveNoSlots() {
        LocalDate saturday = LocalDate.of(2026, 11, 14);
        SchedulingPolicy policy = policy();

        assertThat(policy.isWorkingDay(saturday)).isFalse();
        assertThat(policy.slotsOn(saturday, 30, List.of())).isEmpty();
        assertThat(policy.checkStart(msk(saturday, 10, 0), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.NON_WORKING_DAY);
    }

    @Test
    void slotsFillTheWorkdayOnTheGrid() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        SchedulingPolicy policy = policy();

        List<Slot> hourly = policy.slotsOn(monday, 60, List.of());

        assertThat(hourly).hasSize(17);
        assertThat(hourly.get(0).start()).isEqualTo(msk(monday, 10, 0));
        assertThat(hourly.get(hourly.size() - 1).end()).isEqualTo(msk(monday, 19, 0));
        assertThat(Duration.between(hourly.get(0).start(), hourly.get(0).end()).toMinutes())
                .isEqualTo(60);

        List<Slot> halfHourly = policy.slotsOn(monday, 30, List.of());
        assertThat(halfHourly).hasSize(18);
        assertThat(halfHourly.get(halfHourly.size() - 1).start()).isEqualTo(msk(monday, 18, 30));
    }

    @Test
    void slotsRespectMinimumNotice() {
        SchedulingPolicy policy = policy(scheduleWith(0, 120), MONDAY_MORNING);
        LocalDate monday = LocalDate.of(2026, 11, 9);

        List<Slot> slots = policy.slotsOn(monday, 30, List.of());

        assertThat(slots.get(0).start()).isEqualTo(msk(monday, 11, 0));
        assertThat(policy.checkStart(msk(monday, 10, 30), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.TOO_SOON);
    }

    @Test
    void durationAlignmentIsFifteenMinutes() {
        SchedulingPolicy policy = policy();

        assertThat(policy.durationAlignment(15)).isEqualTo(SchedulingPolicy.DurationAlignment.OK);
        assertThat(policy.durationAlignment(45)).isEqualTo(SchedulingPolicy.DurationAlignment.OK);
        assertThat(policy.durationAlignment(240)).isEqualTo(SchedulingPolicy.DurationAlignment.OK);
        assertThat(policy.durationAlignment(25)).isEqualTo(SchedulingPolicy.DurationAlignment.NOT_ALIGNED);
        assertThat(policy.durationAlignment(0)).isEqualTo(SchedulingPolicy.DurationAlignment.NOT_ALIGNED);
    }

    @Test
    void adjacentIntervalsDoNotOverlapButPartialOnesDo() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        SchedulingPolicy policy = policy();
        BookingInterval hour = new BookingInterval(msk(monday, 10, 0), msk(monday, 11, 0));

        Slot next = slotAfter(policy.slotsOn(monday, 60, List.of(hour)), msk(monday, 10, 0));

        assertThat(next.start()).isEqualTo(msk(monday, 11, 0));
        assertThat(policy.checkStart(msk(monday, 11, 0), 60, List.of(hour)).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OK);
        assertThat(policy.checkStart(msk(monday, 10, 30), 30, List.of(hour)).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.TAKEN);
        assertThat(policy.checkStart(msk(monday, 9, 30), 60, List.of(hour)).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OUTSIDE_WORKING_HOURS);
    }

    @Test
    void overlapPredicateHandlesContainmentAndTouchingEdges() {
        Instant start = msk(LocalDate.of(2026, 11, 9), 10, 0);
        Instant end = msk(LocalDate.of(2026, 11, 9), 11, 0);
        SchedulingPolicy policy = policy();

        assertThat(policy.overlaps(List.of(), start, end)).isFalse();
        assertThat(policy.overlaps(
                List.of(new BookingInterval(start.minus(Duration.ofMinutes(30)), end.plus(Duration.ofMinutes(30)))),
                start,
                end)).isTrue();
        assertThat(policy.overlaps(
                List.of(new BookingInterval(start.plus(Duration.ofMinutes(15)), end.minus(Duration.ofMinutes(15)))),
                start,
                end)).isTrue();
        assertThat(policy.overlaps(
                List.of(new BookingInterval(end, end.plus(Duration.ofMinutes(30)))),
                start,
                end)).isFalse();
    }

    @Test
    void checkStartRejectsSubMinutePrecision() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        SchedulingPolicy policy = policy();

        assertThat(policy.checkStart(msk(monday, 10, 0).plusMillis(500), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OFF_GRID);
        assertThat(policy.checkStart(msk(monday, 10, 0).plusSeconds(30), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OFF_GRID);
    }

    @Test
    void checkStartRejectsEveryCalendarBoundary() {
        LocalDate monday = LocalDate.of(2026, 11, 9);
        SchedulingPolicy policy = policy();

        assertThat(policy.checkStart(msk(monday.minusDays(1), 10, 0), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OUTSIDE_WINDOW);
        assertThat(policy.checkStart(msk(monday.plusDays(14), 10, 0), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OUTSIDE_WINDOW);
        assertThat(policy.checkStart(msk(monday, 9, 30), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OUTSIDE_WORKING_HOURS);
        assertThat(policy.checkStart(msk(monday, 18, 30), 60, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OUTSIDE_WORKING_HOURS);
        assertThat(policy.checkStart(msk(monday, 10, 15), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OFF_GRID);
        assertThat(policy.checkStart(msk(monday, 10, 0).plusSeconds(30), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OFF_GRID);
        assertThat(policy.checkStart(msk(monday, 10, 0), 30, List.of()).status())
                .isEqualTo(SchedulingPolicy.SlotStatus.OK);
    }

    private static Slot slotAfter(List<Slot> slots, Instant previousStart) {
        return slots.stream()
                .filter(slot -> slot.start().isAfter(previousStart))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void windowEndsBeforeANewOwnerLocalDay() {
        SchedulingPolicy.Window window = policy().window();
        ZonedDateTime lastMoment = window.rangeEnd().atZone(ZONE);

        assertThat(lastMoment.toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(lastMoment.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
    }
}
