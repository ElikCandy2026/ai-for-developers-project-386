package com.callbooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.callbooking.api.ApiTime;
import com.callbooking.api.model.Availability;
import com.callbooking.api.model.AvailabilityDay;
import com.callbooking.api.model.Booking;
import com.callbooking.api.model.CreateBookingRequest;
import com.callbooking.api.model.CreateMeetingTypeRequest;
import com.callbooking.api.model.MeetingType;
import com.callbooking.api.model.Owner;
import com.callbooking.api.model.Slot;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingApiIntegrationTest {

    private static final ZoneId OWNER_ZONE = ZoneId.of("Europe/Moscow");

    private static final Path DATABASE_FILE = createDatabaseFile();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.database-path", () -> DATABASE_FILE.toString());
        registry.add("app.seed-demo-data", () -> "false");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM bookings");
        jdbcTemplate.update("DELETE FROM meeting_types");
    }

    @AfterAll
    static void removeDatabase() throws IOException {
        Files.deleteIfExists(DATABASE_FILE);
        Files.deleteIfExists(Path.of(DATABASE_FILE + "-wal"));
        Files.deleteIfExists(Path.of(DATABASE_FILE + "-shm"));
    }

    @Test
    void exposesOwnerAndBookingRules() {
        ResponseEntity<Owner> response = rest.getForEntity("/api/owner", Owner.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Owner owner = response.getBody();
        assertThat(owner).isNotNull();
        assertThat(owner.getDisplayName()).isNotBlank();
        assertThat(owner.getRules().getTimezone()).isEqualTo("Europe/Moscow");
        assertThat(owner.getRules().getSlotStepMinutes()).isEqualTo(30);
        assertThat(owner.getRules().getBookingWindowDays()).isEqualTo(14);
        assertThat(owner.getRules().getWorkdayStart()).isEqualTo("10:00");
        assertThat(owner.getRules().getWorkdayEnd()).isEqualTo("19:00");
        assertThat(owner.getRules().getWorkingDays()).hasSize(5);
    }

    @Test
    void createsMeetingTypeAndListsIt() {
        ResponseEntity<MeetingType> created = rest.postForEntity(
                "/api/meeting-types",
                new CreateMeetingTypeRequest()
                        .name("  Разбор проекта  ")
                        .description("Обсудим архитектуру")
                        .durationMinutes(45),
                MeetingType.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().getId()).isNotBlank();
        assertThat(created.getBody().getName()).isEqualTo("Разбор проекта");
        assertThat(created.getBody().getDurationMinutes()).isEqualTo(45);

        ResponseEntity<MeetingType[]> list = rest.getForEntity("/api/meeting-types", MeetingType[].class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).extracting(MeetingType::getId).contains(created.getBody().getId());
    }

    @Test
    void rejectsDurationThatIsNotMultipleOfFifteen() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/meeting-types",
                new CreateMeetingTypeRequest()
                        .name("Некорректная встреча")
                        .description("")
                        .durationMinutes(25),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_DURATION");
    }

    @Test
    void availabilityCoversFourteenCalendarDaysAndWorkingHours() {
        MeetingType type = createType("Консультация", 60);

        Availability availability = getAvailability(type.getId());
        LocalDate today = LocalDate.now(OWNER_ZONE);

        assertThat(availability.getTimezone()).isEqualTo("Europe/Moscow");
        assertThat(availability.getWindowStartDate()).isEqualTo(today);
        assertThat(availability.getWindowEndDate()).isEqualTo(today.plusDays(13));
        assertThat(availability.getDays()).hasSize(14);
        assertThat(availability.getDays()).extracting(AvailabilityDay::getDate)
                .containsExactlyElementsOf(today.datesUntil(today.plusDays(14)).toList());

        for (AvailabilityDay day : availability.getDays()) {
            if (day.getWeekday() == com.callbooking.api.model.DayOfWeek.SATURDAY
                    || day.getWeekday() == com.callbooking.api.model.DayOfWeek.SUNDAY) {
                assertThat(day.getSlots()).isEmpty();
            }
            for (Slot slot : day.getSlots()) {
                ZonedDateTime localStart = slot.getStart().atZoneSameInstant(OWNER_ZONE);
                ZonedDateTime localEnd = slot.getEnd().atZoneSameInstant(OWNER_ZONE);
                assertThat(localStart.toLocalDate()).isEqualTo(day.getDate());
                assertThat(localStart.toLocalTime()).isBetween(LocalTime.of(10, 0), LocalTime.of(19, 0));
                assertThat(localEnd.toLocalTime()).isBeforeOrEqualTo(LocalTime.of(19, 0));
                assertThat(Duration.between(slot.getStart(), slot.getEnd()).toMinutes()).isEqualTo(60);
                assertThat(localStart.getMinute() % 30).isZero();
                assertThat(localStart.getSecond()).isZero();
            }
        }
    }

    @Test
    void bookingConsumesSlotAndAppearsInOwnerList() {
        MeetingType type = createType("Знакомство", 30);
        Slot slot = firstSlotOnFutureDay(type.getId());

        ResponseEntity<Booking> created = postBooking(
                type.getId(), slot.getStart().toInstant(), "Иван Петров", "ivan@example.com", "Хочу обсудить проект");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(Duration.between(created.getBody().getStart(), created.getBody().getEnd()).toMinutes())
                .isEqualTo(30);
        assertThat(created.getBody().getMeetingType().getId()).isEqualTo(type.getId());

        Availability refreshed = getAvailability(type.getId());
        assertThat(allSlotStarts(refreshed)).doesNotContain(slot.getStart().toInstant());

        ResponseEntity<Booking[]> bookings = rest.getForEntity("/api/bookings", Booking[].class);
        assertThat(bookings.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bookings.getBody()).hasSize(1);
        assertThat(bookings.getBody()[0].getGuestEmail()).isEqualTo("ivan@example.com");
    }

    @Test
    void rejectsPartialOverlapBetweenDifferentMeetingTypesAndAllowsAdjacentBooking() {
        MeetingType sixty = createType("Час", 60);
        MeetingType thirty = createType("Полчаса", 30);

        Slot start = firstSlotOnFutureDay(sixty.getId());

        ResponseEntity<Booking> first = postBooking(
                sixty.getId(), start.getStart().toInstant(), "Первый", "first@example.com", null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> partialOverlap = postBookingRaw(
                thirty.getId(), start.getStart().toInstant().plus(Duration.ofMinutes(30)),
                "Второй", "second@example.com");
        assertThat(partialOverlap.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(partialOverlap.getBody()).contains("SLOT_TAKEN");

        ResponseEntity<Booking> adjacent = postBooking(
                thirty.getId(), start.getStart().toInstant().plus(Duration.ofMinutes(60)),
                "Третий", "third@example.com", null);
        assertThat(adjacent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void concurrentBookingsOfTheSameSlotProduceExactlyOneReservation() throws Exception {
        MeetingType type = createType("Одновременная запись", 30);
        MeetingType otherType = createType("Другой формат", 60);
        Slot slot = firstSlotOnFutureDay(type.getId());
        int attempts = 10;

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    String meetingTypeId = index % 2 == 0 ? type.getId() : otherType.getId();
                    return postBookingRaw(
                            meetingTypeId,
                            slot.getStart().toInstant(),
                            "Гость " + index,
                            "guest" + index + "@example.com")
                            .getStatusCode()
                            .value();
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(15, TimeUnit.SECONDS));
            }

            assertThat(statuses).containsOnly(
                    HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
            assertThat(statuses.stream().filter(status -> status == HttpStatus.CREATED.value())).hasSize(1);

            ResponseEntity<Booking[]> bookings = rest.getForEntity("/api/bookings", Booking[].class);
            assertThat(bookings.getBody()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsBookingOutsideWindow() {
        MeetingType type = createType("Вне окна", 30);
        LocalDate windowEnd = getAvailability(type.getId()).getWindowEndDate();
        Instant outside = windowEnd.plusDays(1).atTime(10, 0).atZone(OWNER_ZONE).toInstant();

        ResponseEntity<String> response = postBookingRaw(
                type.getId(), outside, "Гость", "guest@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("SLOT_UNAVAILABLE");
    }

    @Test
    void rejectsStartThatDoesNotMatchSlotStep() {
        MeetingType type = createType("Сетка", 30);
        Availability availability = getAvailability(type.getId());
        LocalDate date = firstFutureDateWithSlots(availability);
        Instant offGrid = date.atTime(10, 15).atZone(OWNER_ZONE).toInstant();

        ResponseEntity<String> response = postBookingRaw(
                type.getId(), offGrid, "Гость", "guest@example.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("SLOT_UNAVAILABLE");
    }

    @Test
    void validatesGuestData() {
        MeetingType type = createType("Валидация", 30);
        Slot slot = firstSlotOnFutureDay(type.getId());

        ResponseEntity<String> emptyName = postBookingRaw(
                type.getId(), slot.getStart().toInstant(), "   ", "guest@example.com");
        assertThat(emptyName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> badEmail = postBookingRaw(
                type.getId(), slot.getStart().toInstant(), "Гость", "not-an-email");
        assertThat(badEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returnsNotFoundForUnknownMeetingType() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/meeting-types/" + UUID.randomUUID() + "/availability", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("MEETING_TYPE_NOT_FOUND");
    }

    private MeetingType createType(String name, int durationMinutes) {
        ResponseEntity<MeetingType> response = rest.postForEntity(
                "/api/meeting-types",
                new CreateMeetingTypeRequest()
                        .name(name)
                        .description("Описание")
                        .durationMinutes(durationMinutes),
                MeetingType.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Availability getAvailability(String meetingTypeId) {
        ResponseEntity<Availability> response = rest.getForEntity(
                "/api/meeting-types/{id}/availability", Availability.class, meetingTypeId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private Slot firstSlotOnFutureDay(String meetingTypeId) {
        Availability availability = getAvailability(meetingTypeId);
        LocalDate today = LocalDate.now(OWNER_ZONE);
        return availability.getDays().stream()
                .filter(day -> day.getDate().isAfter(today))
                .filter(day -> !day.getSlots().isEmpty())
                .findFirst()
                .flatMap(day -> day.getSlots().stream().findFirst())
                .orElseThrow(() -> new AssertionError("В окне не оказалось свободных слотов"));
    }

    private LocalDate firstFutureDateWithSlots(Availability availability) {
        LocalDate today = LocalDate.now(OWNER_ZONE);
        return availability.getDays().stream()
                .filter(day -> day.getDate().isAfter(today))
                .filter(day -> !day.getSlots().isEmpty())
                .map(AvailabilityDay::getDate)
                .findFirst()
                .orElseThrow(() -> new AssertionError("В окне не оказалось свободных слотов"));
    }

    private List<Instant> allSlotStarts(Availability availability) {
        return availability.getDays().stream()
                .flatMap(day -> day.getSlots().stream())
                .map(slot -> slot.getStart().toInstant())
                .toList();
    }

    private ResponseEntity<String> postBookingRaw(
            String meetingTypeId, Instant start, String guestName, String guestEmail) {
        return rest.postForEntity(
                "/api/bookings",
                bookingRequest(meetingTypeId, start, guestName, guestEmail),
                String.class);
    }

    private ResponseEntity<Booking> postBooking(
            String meetingTypeId, Instant start, String guestName, String guestEmail, String notes) {
        return rest.postForEntity(
                "/api/bookings",
                bookingRequest(meetingTypeId, start, guestName, guestEmail).notes(notes),
                Booking.class);
    }

    private CreateBookingRequest bookingRequest(
            String meetingTypeId, Instant start, String guestName, String guestEmail) {
        return new CreateBookingRequest()
                .meetingTypeId(meetingTypeId)
                .start(ApiTime.utc(start))
                .guestName(guestName)
                .guestEmail(guestEmail);
    }

    private static Path createDatabaseFile() {
        try {
            return Files.createTempFile("call-booking-test-", ".db");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create test database", exception);
        }
    }
}
