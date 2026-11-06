import type { DayOfWeek } from '../api/generated';

export const WEEKDAY_SHORT: Record<DayOfWeek, string> = {
  MONDAY: 'Пн',
  TUESDAY: 'Вт',
  WEDNESDAY: 'Ср',
  THURSDAY: 'Чт',
  FRIDAY: 'Пт',
  SATURDAY: 'Сб',
  SUNDAY: 'Вс',
};

export const WEEKDAY_LONG: Record<DayOfWeek, string> = {
  MONDAY: 'Понедельник',
  TUESDAY: 'Вторник',
  WEDNESDAY: 'Среда',
  THURSDAY: 'Четверг',
  FRIDAY: 'Пятница',
  SATURDAY: 'Суббота',
  SUNDAY: 'Воскресенье',
};

export function formatDuration(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} мин`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  const hourWord = hours === 1 ? 'час' : hours < 5 ? 'часа' : 'часов';
  if (rest === 0) {
    return `${hours} ${hourWord}`;
  }
  return `${hours} ${hourWord} ${rest} мин`;
}

export function formatDurationShort(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} мин`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} ч` : `${hours} ч ${rest} мин`;
}

/** Availability dates are owner-local calendar dates, so format them as UTC noon. */
function localDateAsUtcNoon(date: string): Date {
  return new Date(`${date}T12:00:00Z`);
}

export function formatDayNumber(date: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric',
    timeZone: 'UTC',
  }).format(localDateAsUtcNoon(date));
}

export function formatMonthShort(date: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    month: 'short',
    timeZone: 'UTC',
  })
    .format(localDateAsUtcNoon(date))
    .replace('.', '');
}

export function formatMonthLong(date: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    month: 'long',
    timeZone: 'UTC',
  }).format(localDateAsUtcNoon(date));
}

export function formatDateRange(start: string, end: string): string {
  const startMonth = formatMonthLong(start);
  const endMonth = formatMonthLong(end);
  const year = end.slice(0, 4);
  if (start.slice(0, 7) === end.slice(0, 7)) {
    return `${formatDayNumber(start)}–${formatDayNumber(end)} ${endMonth} ${year}`;
  }
  return `${formatDayNumber(start)} ${startMonth} — ${formatDayNumber(end)} ${endMonth} ${year}`;
}

export function formatLocalDateLong(date: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  }).format(localDateAsUtcNoon(date));
}

export function formatTime(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone,
  }).format(new Date(iso));
}

export function formatTimeRange(start: string, end: string, timeZone: string): string {
  return `${formatTime(start, timeZone)}–${formatTime(end, timeZone)}`;
}

export function formatBookingDate(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat('ru-RU', {
    weekday: 'short',
    day: 'numeric',
    month: 'long',
    timeZone,
  }).format(new Date(iso));
}

export function formatBookingDayParts(iso: string, timeZone: string): {
  weekday: string;
  day: string;
  month: string;
} {
  const date = new Date(iso);
  const weekday = new Intl.DateTimeFormat('ru-RU', { weekday: 'short', timeZone }).format(date);
  const day = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', timeZone }).format(date);
  const month = new Intl.DateTimeFormat('ru-RU', { month: 'short', timeZone })
    .format(date)
    .replace('.', '');
  return { weekday, day, month };
}

export function formatTimezoneOffset(timeZone: string): string {
  try {
    const parts = new Intl.DateTimeFormat('ru-RU', {
      timeZone,
      timeZoneName: 'shortOffset',
    }).formatToParts(new Date());
    return parts.find((part) => part.type === 'timeZoneName')?.value ?? timeZone;
  } catch {
    return timeZone;
  }
}

export function isoToLocalDate(iso: string, timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone,
  }).formatToParts(new Date(iso));
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
}

export function todayInZone(timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone,
  }).formatToParts(new Date());
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  return `${value('year')}-${value('month')}-${value('day')}`;
}

export function addDays(date: string, days: number): string {
  const parsed = new Date(`${date}T12:00:00Z`);
  parsed.setUTCDate(parsed.getUTCDate() + days);
  return parsed.toISOString().slice(0, 10);
}

export function relativeDateLabel(date: string, today: string): string | null {
  if (date === today) {
    return 'Сегодня';
  }
  if (date === addDays(today, 1)) {
    return 'Завтра';
  }
  return null;
}
