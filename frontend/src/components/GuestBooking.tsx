import { useEffect, useMemo, useState } from 'react';

import type { Availability, Booking, MeetingType, Owner, Slot } from '../api/generated';
import { getAvailability } from '../api/generated';
import { apiErrorMessage } from '../lib/apiError';
import {
  formatBookingDate,
  formatDateRange,
  formatDuration,
  formatDurationShort,
  formatLocalDateLong,
  formatMonthLong,
  formatTime,
  formatTimezoneOffset,
  relativeDateLabel,
  todayInZone,
  WEEKDAY_LONG,
  WEEKDAY_SHORT,
} from '../lib/format';
import { AlertIcon, ArrowRightIcon, CalendarIcon, CheckIcon, ClockIcon, SparkIcon, VideoIcon } from './Icons';
import { BookingDialog } from './BookingDialog';

type GuestBookingProps = {
  owner: Owner;
  meetingTypes: MeetingType[];
  onOpenOwner: () => void;
};

export function GuestBooking({ owner, meetingTypes, onOpenOwner }: GuestBookingProps) {
  const timezone = owner.rules.timezone;
  const timezoneOffset = useMemo(() => formatTimezoneOffset(timezone), [timezone]);
  const today = todayInZone(timezone);

  const [selectedTypeId, setSelectedTypeId] = useState<string | null>(meetingTypes[0]?.id ?? null);
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);
  const [lastBooking, setLastBooking] = useState<Booking | null>(null);

  const selectedType = meetingTypes.find((type) => type.id === selectedTypeId) ?? null;
  const selectedDay = availability?.days.find((day) => day.date === selectedDate) ?? null;
  const selectedDateLabel = selectedDay
    ? formatLocalDateLong(selectedDay.date)
    : 'Выберите дату';

  useEffect(() => {
    if (!selectedTypeId && meetingTypes.length > 0) {
      setSelectedTypeId(meetingTypes[0].id);
    }
  }, [meetingTypes, selectedTypeId]);

  useEffect(() => {
    if (!selectedTypeId) {
      setAvailability(null);
      setSelectedDate(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    setAvailability(null);

    getAvailability({ path: { meetingTypeId: selectedTypeId } })
      .then((result) => {
        if (cancelled) {
          return;
        }
        if (result.error || !result.data) {
          setError(apiErrorMessage(result.error, 'Не удалось загрузить свободные слоты.'));
          return;
        }
        const data = result.data;
        setAvailability(data);
        setSelectedDate((previous) => {
          const stillAvailable = data.days.find(
            (day) => day.date === previous && day.slots.length > 0,
          );
          if (stillAvailable) {
            return previous;
          }
          return data.days.find((day) => day.slots.length > 0)?.date ?? data.days[0]?.date ?? null;
        });
      })
      .catch(() => {
        if (!cancelled) {
          setError('Сервер недоступен. Проверьте соединение и обновите страницу.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedTypeId, refreshToken]);

  function handleBooked(booking: Booking) {
    setLastBooking(booking);
    setRefreshToken((value) => value + 1);
  }

  function handleSlotUnavailable() {
    setRefreshToken((value) => value + 1);
  }

  const availableDayCount = availability?.days.filter((day) => day.slots.length > 0).length ?? 0;
  const workdayLabel = useMemo(() => {
    const workingDays = owner.rules.workingDays;
    if (workingDays.length === 0) {
      return '—';
    }
    return `${WEEKDAY_SHORT[workingDays[0]]}–${WEEKDAY_SHORT[workingDays[workingDays.length - 1]]}`;
  }, [owner.rules.workingDays]);

  return (
    <main className="guest-page">
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">
            <span className="live-dot" />
            Открыта запись на звонок
          </span>
          <h1>
            Найдите время
            <br />
            для <span>спокойного разговора.</span>
          </h1>
          <p className="hero-lead">
            Выберите формат встречи и удобный слот. Запись займёт меньше минуты — без регистрации
            и переписки.
          </p>
          <div className="hero-meta">
            <span className="meta-pill">
              <CalendarIcon />
              Ближайшие 14 дней
            </span>
            <span className="meta-pill">
              <ClockIcon />
              {workdayLabel} · {owner.rules.workdayStart}–{owner.rules.workdayEnd}
            </span>
            <span className="meta-pill">
              <SparkIcon />
              {timezoneOffset}
            </span>
          </div>
        </div>

        <aside className="owner-spotlight">
          <div className="spotlight-top">
            <span className="spotlight-label">Владелец календаря</span>
            <span className="availability-badge">
              <span className="live-dot" />
              Онлайн
            </span>
          </div>
          <div className="owner-avatar">АС</div>
          <h2>{owner.displayName}</h2>
          <p className="muted">Онлайн-встречи по видеосвязи</p>
          <div className="spotlight-rules">
            <div>
              <span>Часовой пояс</span>
              <strong>{timezone}</strong>
            </div>
            <div>
              <span>Рабочие часы</span>
              <strong>{owner.rules.workdayStart}–{owner.rules.workdayEnd}</strong>
            </div>
            <div>
              <span>Окно записи</span>
              <strong>{owner.rules.bookingWindowDays} календарных дней</strong>
            </div>
          </div>
          <div className="spotlight-footer">
            <span className="mini-calendar-dots" aria-hidden="true">
              {Array.from({ length: 14 }, (_, index) => (
                <i key={index} className={index < 9 ? 'filled' : ''} />
              ))}
            </span>
            <span>{availableDayCount > 0 ? `${availableDayCount} дн. со слотами` : 'Слоты обновляются'}</span>
          </div>
        </aside>
      </section>

      {lastBooking && (
        <div className="inline-success" role="status">
          <CheckIcon />
          <span>
            Запись создана: {formatBookingDate(lastBooking.start, timezone)} в{' '}
            {formatTime(lastBooking.start, timezone)}. Она уже видна владельцу в разделе
            «Предстоящие встречи».
          </span>
          <button type="button" className="inline-link" onClick={() => setLastBooking(null)}>
            Скрыть
          </button>
        </div>
      )}

      <section className="guest-section">
        <div className="section-heading">
          <span className="step-number">01</span>
          <div>
            <h2>Тип встречи</h2>
            <p>Длительность слота совпадает с длительностью формата.</p>
          </div>
        </div>

        {meetingTypes.length === 0 ? (
          <div className="empty-state card">
            <div className="empty-icon">
              <CalendarIcon />
            </div>
            <h3>Пока нет доступных типов встреч</h3>
            <p>Владелец ещё не создал ни одного формата. Это можно сделать в кабинете владельца.</p>
            <button className="secondary-button" type="button" onClick={onOpenOwner}>
              Перейти в кабинет
              <ArrowRightIcon />
            </button>
          </div>
        ) : (
          <div className="type-grid">
            {meetingTypes.map((type, index) => {
              const selected = type.id === selectedTypeId;
              return (
                <button
                  key={type.id}
                  type="button"
                  className={`type-card ${selected ? 'selected' : ''}`}
                  onClick={() => setSelectedTypeId(type.id)}
                  aria-pressed={selected}
                  style={{ animationDelay: `${index * 60}ms` }}
                >
                  <div className="type-card-top">
                    <span className="type-icon">
                      <VideoIcon />
                    </span>
                    <span className="duration-chip">{formatDurationShort(type.durationMinutes)}</span>
                  </div>
                  <h3>{type.name}</h3>
                  <p>{type.description || 'Без описания'}</p>
                  <span className="type-card-action">
                    {selected ? (
                      <>
                        <CheckIcon />
                        Выбрано
                      </>
                    ) : (
                      <>
                        Выбрать
                        <ArrowRightIcon />
                      </>
                    )}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </section>

      <section className="guest-section" id="calendar">
        <div className="section-heading">
          <span className="step-number">02</span>
          <div>
            <h2>Дата и время</h2>
            <p>Свободные старты с шагом {owner.rules.slotStepMinutes} минут.</p>
          </div>
        </div>

        <div className="booking-panel card">
          <div className="booking-panel-head">
            <div>
              <span className="panel-kicker">{selectedType ? selectedType.name : 'Выберите тип встречи'}</span>
              <h3>
                {selectedType
                  ? `${formatDuration(selectedType.durationMinutes)} · ${timezoneOffset}`
                  : 'Календарь'}
              </h3>
            </div>
            <span className="zone-badge">
              <ClockIcon />
              {timezone}
            </span>
          </div>

          {!selectedType && (
            <div className="empty-state compact">
              <p>Сначала выберите тип встречи — после этого появится календарь.</p>
            </div>
          )}

          {selectedType && loading && (
            <div className="booking-loading" aria-label="Загрузка слотов">
              <div className="skeleton date-skeletons">
                {Array.from({ length: 7 }, (_, index) => (
                  <span key={index} className="skeleton-card" />
                ))}
              </div>
              <div className="skeleton slot-skeletons">
                {Array.from({ length: 8 }, (_, index) => (
                  <span key={index} className="skeleton-pill" />
                ))}
              </div>
            </div>
          )}

          {selectedType && !loading && error && (
            <div className="alert alert-error" role="alert">
              <AlertIcon />
              <span>{error}</span>
              <button className="inline-link" type="button" onClick={() => setRefreshToken((v) => v + 1)}>
                Повторить
              </button>
            </div>
          )}

          {selectedType && !loading && !error && availability && (
            <>
              <div className="date-rail" role="list" aria-label="Даты окна записи">
                {availability.days.map((day) => {
                  const disabled = day.slots.length === 0;
                  const active = day.date === selectedDate;
                  const relative = relativeDateLabel(day.date, today);
                  return (
                    <button
                      key={day.date}
                      type="button"
                      role="listitem"
                      className={`date-card ${active ? 'selected' : ''} ${disabled ? 'disabled' : ''}`}
                      onClick={() => setSelectedDate(day.date)}
                      disabled={disabled}
                      aria-pressed={active}
                      aria-label={`${WEEKDAY_LONG[day.weekday]}, ${day.date}. ${disabled ? 'Нет свободных слотов' : `${day.slots.length} слотов`}`}
                    >
                      <span className="date-weekday">{relative ?? WEEKDAY_SHORT[day.weekday]}</span>
                      <strong className="date-number">{day.date.slice(8, 10)}</strong>
                      <span className="date-month">{formatMonthLong(day.date).slice(0, 3)}</span>
                      <span className={`date-dot ${disabled ? 'muted' : ''}`} />
                    </button>
                  );
                })}
              </div>

              <div className="slots-panel">
                <div className="slots-head">
                  <div>
                    <h4>{selectedDateLabel}</h4>
                    <p>
                      {selectedDay
                        ? `${selectedDay.slots.length} свободных слотов`
                        : 'Выберите день в календаре'}
                    </p>
                  </div>
                  <span className="slots-zone">
                    <ClockIcon />
                    {timezoneOffset}
                  </span>
                </div>

                {selectedDay && selectedDay.slots.length > 0 ? (
                  <div className="slot-grid">
                    {selectedDay.slots.map((slot) => (
                      <button
                        key={slot.start}
                        type="button"
                        className="slot-button"
                        onClick={() => setSelectedSlot(slot)}
                      >
                        {formatTime(slot.start, timezone)}
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="empty-state compact">
                    <p>На этот день свободных слотов нет. Выберите другую дату.</p>
                  </div>
                )}

                <p className="slots-footnote">
                  <CalendarIcon />
                  Окно записи: {formatDateRange(availability.windowStartDate, availability.windowEndDate)}.
                  Время указано по часовому поясу владельца.
                </p>
              </div>
            </>
          )}
        </div>
      </section>

      {selectedSlot && selectedType && (
        <BookingDialog
          owner={owner}
          meetingType={selectedType}
          slot={selectedSlot}
          date={selectedDate ?? selectedSlot.start.slice(0, 10)}
          onClose={() => setSelectedSlot(null)}
          onBooked={handleBooked}
          onSlotUnavailable={handleSlotUnavailable}
        />
      )}
    </main>
  );
}
