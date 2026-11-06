import "@typespec/http";
import "@typespec/openapi";
import "@typespec/openapi3";

using TypeSpec.Http;
using TypeSpec.OpenAPI;

/**
 * API сервиса «Запись на звонок».
 *
 * Владелец календаря один и задан заранее. Авторизации нет: гость и владелец
 * работают с одним и тем же API. Все моменты времени передаются в UTC
 * (ISO 8601, например `2026-11-09T07:00:00Z`). Локальное время и границы
 * рабочего дня считаются в часовом поясе владельца.
 *
 * Правила записи:
 * - часовой пояс владельца: `Europe/Moscow` (UTC+3, без перехода на летнее время);
 * - рабочие дни: понедельник–пятница, 10:00–19:00 по времени владельца;
 * - окно записи: 14 календарных дней, начиная с сегодняшнего дня по времени
 *   владельца (смещается каждый календарный день);
 * - шаг доступных стартов: 30 минут от начала рабочего дня;
 * - длительность слота равна длительности типа встречи (15–240 минут, кратно 15);
 * - интервалы встреч не пересекаются, в том числе частично и между разными
 *   типами встреч; встреча может начинаться ровно в момент окончания другой.
 */
@service(#{ title: "Call Booking API" })
@info(#{ version: "1.0.0" })
@server("http://localhost:8080", "Local development server")
namespace CallBooking;

/** День недели в ISO-8601 (понедельник = MONDAY). */
enum DayOfWeek {
  MONDAY,
  TUESDAY,
  WEDNESDAY,
  THURSDAY,
  FRIDAY,
  SATURDAY,
  SUNDAY,
}

/** Правила календаря владельца. Возвращаются клиенту, чтобы интерфейс не дублировал их. */
model BookingRules {
  /** IANA-часовой пояс владельца. */
  timezone: string;

  /** Рабочие дни владельца. */
  workingDays: DayOfWeek[];

  /** Начало рабочего дня в формате HH:mm по часовому поясу владельца. */
  @pattern("^([01][0-9]|2[0-3]):[0-5][0-9]$")
  workdayStart: string;

  /** Окончание рабочего дня в формате HH:mm по часовому поясу владельца. */
  @pattern("^([01][0-9]|2[0-3]):[0-5][0-9]$")
  workdayEnd: string;

  /** Шаг доступных стартов в минутах. */
  @minValue(1)
  slotStepMinutes: int32;

  /** Длина окна записи в календарных днях. */
  @minValue(1)
  bookingWindowDays: int32;

  /** Сдвиг начала окна от сегодняшнего дня: 0 — сегодня, 1 — завтра. */
  @minValue(0)
  bookingWindowStartOffsetDays: int32;

  /** Минимальный запас времени до начала встречи в минутах. */
  @minValue(0)
  minimumNoticeMinutes: int32;

  /** Минимальная длительность встречи в минутах. */
  @minValue(1)
  minimumDurationMinutes: int32;

  /** Максимальная длительность встречи в минутах. */
  @minValue(1)
  maximumDurationMinutes: int32;
}

/** Владелец календаря. */
model Owner {
  id: string;
  displayName: string;
  email: string;
  rules: BookingRules;
}

/** Тип встречи, созданный владельцем. */
model MeetingType {
  id: string;
  name: string;
  description: string;

  /** Длительность встречи в минутах. */
  @minValue(1)
  durationMinutes: int32;

  /** Момент создания в UTC. */
  createdAt: utcDateTime;
}

/** Краткая информация о типе встречи, встроенная в запись. */
model MeetingTypeSummary {
  id: string;
  name: string;
  description: string;
  durationMinutes: int32;
}

/** Запрос на создание типа встречи. */
model CreateMeetingTypeRequest {
  @minLength(1)
  @maxLength(80)
  name: string;

  @maxLength(1000)
  description: string;

  /** Длительность в минутах: 15–240, кратно 15. */
  @minValue(15)
  @maxValue(240)
  durationMinutes: int32;
}

/** Свободный слот записи. */
model Slot {
  /** Начало слота в UTC. */
  start: utcDateTime;

  /** Окончание слота в UTC. */
  end: utcDateTime;
}

/** День окна записи. */
model AvailabilityDay {
  /** Локальная дата владельца. */
  date: plainDate;

  weekday: DayOfWeek;

  /** Свободные слоты дня. Пустой массив, если записаться нельзя. */
  slots: Slot[];
}

/** Календарь свободных слотов для выбранного типа встречи. */
model Availability {
  meetingType: MeetingTypeSummary;
  timezone: string;

  /** Первый день окна записи включительно. */
  windowStartDate: plainDate;

  /** Последний день окна записи включительно. */
  windowEndDate: plainDate;

  /** Шаг стартов в минутах. */
  slotStepMinutes: int32;

  /** Длительность слота в минутах. */
  durationMinutes: int32;

  /** Все дни окна, включая выходные и дни без свободных слотов. */
  days: AvailabilityDay[];
}

/** Запрос на запись на звонок. */
model CreateBookingRequest {
  meetingTypeId: string;

  /** Начало встречи в UTC. Должно совпадать с одним из свободных слотов. */
  start: utcDateTime;

  @minLength(1)
  @maxLength(120)
  guestName: string;

  @format("email")
  @minLength(3)
  @maxLength(254)
  guestEmail: string;

  @maxLength(1000)
  notes?: string;
}

/** Созданная запись. */
model Booking {
  id: string;
  meetingType: MeetingTypeSummary;
  guestName: string;
  guestEmail: string;
  notes?: string;

  /** Начало встречи в UTC. */
  start: utcDateTime;

  /** Окончание встречи в UTC. */
  end: utcDateTime;

  /** Момент создания записи в UTC. */
  createdAt: utcDateTime;
}

/** Ошибка валидации запроса. */
@error
model BadRequestError {
  @statusCode statusCode: 400;
  code: string;
  message: string;
}

/** Ресурс не найден. */
@error
model NotFoundError {
  @statusCode statusCode: 404;
  code: string;
  message: string;
}

/** Слот недоступен: пересекается с другой встречей или вышел из окна записи. */
@error
model ConflictError {
  @statusCode statusCode: 409;
  code: string;
  message: string;
}

@tag("Owner")
@route("/api/owner")
@get
/** Данные единственного владельца календаря и правила записи. */
op getOwner(): Owner;

@tag("Meeting types")
@route("/api/meeting-types")
@get
/** Список типов встреч владельца в порядке создания. */
op listMeetingTypes(): MeetingType[];

@tag("Meeting types")
@route("/api/meeting-types")
@post
/** Создать новый тип встречи. */
op createMeetingType(@body request: CreateMeetingTypeRequest): {
  @statusCode statusCode: 201;
  @body meetingType: MeetingType;
} | BadRequestError;

@tag("Meeting types")
@route("/api/meeting-types/{meetingTypeId}")
@get
/** Получить тип встречи по идентификатору. */
op getMeetingType(@path meetingTypeId: string): MeetingType | NotFoundError;

@tag("Availability")
@route("/api/meeting-types/{meetingTypeId}/availability")
@get
/**
 * Свободные слоты типа встречи на актуальное 14-дневное окно.
 * Окно начинается с сегодняшнего дня по времени владельца.
 */
op getAvailability(@path meetingTypeId: string): Availability | NotFoundError;

@tag("Bookings")
@route("/api/bookings")
@post
/**
 * Записать гостя на свободный слот. Сервер сам вычисляет окончание встречи,
 * проверяет окно, рабочие часы, шаг слотов и пересечения. Два одновременных
 * запроса на один слот не могут оба завершиться успехом.
 */
op createBooking(@body request: CreateBookingRequest): {
  @statusCode statusCode: 201;
  @body booking: Booking;
} | BadRequestError | NotFoundError | ConflictError;

@tag("Bookings")
@route("/api/bookings")
@get
/**
 * Предстоящие встречи всех типов, упорядоченные по началу.
 * По умолчанию возвращаются только встречи, которые ещё не начались.
 */
op listBookings(@query includePast?: boolean): Booking[];
