import { useEffect, useMemo, useState, type FormEvent } from 'react';

import type { MeetingType, Owner } from '../data/api';
import { createMeetingType, fetchUpcomingBookings } from '../data/api';
import { useResource } from '../data/useResource';
import {
  formatBookingDayParts,
  formatDuration,
  formatDurationShort,
  formatTimeRange,
  formatTimezoneOffset,
  isoToLocalDate,
  todayInZone,
} from '../lib/format';
import {
  AlertIcon,
  CalendarIcon,
  CheckIcon,
  ClockIcon,
  MailIcon,
  NotesIcon,
  PhoneIcon,
  PlusIcon,
  RefreshIcon,
  SparkIcon,
  UserIcon,
  VideoIcon,
} from './Icons';

type OwnerDashboardProps = {
  owner: Owner;
  meetingTypes: MeetingType[];
  onTypeCreated: (meetingType: MeetingType) => void;
  onOpenGuest: () => void;
};

export function OwnerDashboard({
  owner,
  meetingTypes,
  onTypeCreated,
  onOpenGuest,
}: OwnerDashboardProps) {
  const timezone = owner.rules.timezone;
  const timezoneOffset = useMemo(() => formatTimezoneOffset(timezone), [timezone]);
  const today = todayInZone(timezone);

  const {
    data: bookingsData,
    loading: bookingsLoading,
    error: bookingsError,
    reload: reloadBookings,
  } = useResource(fetchUpcomingBookings, []);
  const bookings = bookingsData ?? [];

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [duration, setDuration] = useState(30);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createdName, setCreatedName] = useState<string | null>(null);

  const initials = owner.displayName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');

  useEffect(() => {
    if (!createdName) {
      return;
    }
    const timeout = window.setTimeout(() => setCreatedName(null), 4000);
    return () => window.clearTimeout(timeout);
  }, [createdName]);

  const durationOptions = useMemo(() => {
    const options: number[] = [];
    for (
      let value = owner.rules.minimumDurationMinutes;
      value <= owner.rules.maximumDurationMinutes;
      value += 15
    ) {
      options.push(value);
    }
    return options;
  }, [owner.rules.maximumDurationMinutes, owner.rules.minimumDurationMinutes]);

  async function handleCreateType(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (creating) {
      return;
    }
    setCreateError(null);
    setCreating(true);

    try {
      const result = await createMeetingType({
        name: name.trim(),
        description: description.trim(),
        durationMinutes: duration,
      });
      if (!result.ok) {
        setCreateError(result.error.message);
        return;
      }
      onTypeCreated(result.data);
      setCreatedName(result.data.name);
      setName('');
      setDescription('');
      setDuration(30);
    } finally {
      setCreating(false);
    }
  }

  return (
    <main className="owner-page">
      <section className="owner-hero">
        <div>
          <span className="eyebrow">
            <span className="live-dot" />
            Кабинет владельца
          </span>
          <h1>Ваш календарь звонков</h1>
          <p className="hero-lead">
            Создавайте форматы встреч и следите за предстоящими записями. Гостям не нужна
            регистрация.
          </p>
        </div>
        <div className="owner-profile-card">
          <div className="owner-avatar small">{initials}</div>
          <div>
            <strong>{owner.displayName}</strong>
            <span>{owner.email}</span>
          </div>
          <span className="owner-status">
            <span className="live-dot" />
            Принимает звонки
          </span>
        </div>
      </section>

      <section className="stats-row">
        <div className="stat-card">
          <span className="stat-icon"><VideoIcon /></span>
          <div>
            <span className="stat-label">Типов встреч</span>
            <strong>{meetingTypes.length}</strong>
          </div>
        </div>
        <div className="stat-card">
          <span className="stat-icon"><CalendarIcon /></span>
          <div>
            <span className="stat-label">Предстоящих встреч</span>
            <strong>{bookingsLoading ? '—' : bookings.length}</strong>
          </div>
        </div>
        <div className="stat-card wide">
          <span className="stat-icon"><ClockIcon /></span>
          <div>
            <span className="stat-label">Рабочие часы</span>
            <strong>
              {owner.rules.workdayStart}–{owner.rules.workdayEnd}
              <small>{timezone} · {timezoneOffset}</small>
            </strong>
          </div>
        </div>
      </section>

      <div className="owner-layout">
        <aside className="owner-sidebar">
          <section className="card form-card">
            <div className="card-heading">
              <span className="card-icon"><PlusIcon /></span>
              <div>
                <h2>Новый тип встречи</h2>
                <p>Появится у гостей сразу после создания.</p>
              </div>
            </div>

            <form className="create-form" onSubmit={handleCreateType}>
              <label className="form-field">
                <span>Название</span>
                <input
                  className="input"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Например, Знакомство"
                  maxLength={80}
                  required
                />
              </label>

              <label className="form-field">
                <span>Описание</span>
                <textarea
                  className="input textarea"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  placeholder="О чём поговорим на встрече?"
                  maxLength={1000}
                  rows={3}
                />
              </label>

              <label className="form-field">
                <span>Длительность</span>
                <div className="select-wrap">
                  <select
                    className="input select"
                    value={duration}
                    onChange={(event) => setDuration(Number(event.target.value))}
                  >
                    {durationOptions.map((value) => (
                      <option key={value} value={value}>
                        {formatDuration(value)}
                      </option>
                    ))}
                  </select>
                </div>
                <small className="field-hint">Шаг слотов — {owner.rules.slotStepMinutes} минут.</small>
              </label>

              {createError && (
                <div className="alert alert-error" role="alert">
                  <AlertIcon />
                  <span>{createError}</span>
                </div>
              )}

              {createdName && (
                <div className="alert alert-success" role="status">
                  <CheckIcon />
                  <span>Тип «{createdName}» создан.</span>
                </div>
              )}

              <button className="primary-button full-width" type="submit" disabled={creating}>
                {creating ? <span className="spinner" /> : (
                  <>
                    <PlusIcon />
                    Создать тип встречи
                  </>
                )}
              </button>
            </form>
          </section>

          <section className="card tip-card">
            <SparkIcon />
            <div>
              <strong>Как это работает</strong>
              <p>
                Гость выбирает тип встречи, дату и свободный слот. Сервер сам проверяет пересечения
                и не даёт занять одно время дважды.
              </p>
            </div>
          </section>
        </aside>

        <div className="owner-content">
          <section className="card bookings-card">
            <div className="card-heading between">
              <div className="heading-with-icon">
                <span className="card-icon"><CalendarIcon /></span>
                <div>
                  <h2>Предстоящие встречи</h2>
                  <p>Все типы встреч, отсортированные по началу.</p>
                </div>
              </div>
              <button
                className="secondary-button small"
                type="button"
                onClick={reloadBookings}
                disabled={bookingsLoading}
              >
                <RefreshIcon className={bookingsLoading ? 'spin' : ''} />
                Обновить
              </button>
            </div>

            {bookingsError && (
              <div className="alert alert-error" role="alert">
                <AlertIcon />
                <span>{bookingsError.message}</span>
              </div>
            )}

            {bookingsLoading ? (
              <div className="booking-list loading-list">
                {Array.from({ length: 3 }, (_, index) => (
                  <div key={index} className="skeleton-booking">
                    <span className="skeleton-block date" />
                    <span className="skeleton-block main" />
                  </div>
                ))}
              </div>
            ) : bookings.length === 0 ? (
              <div className="empty-state compact">
                <div className="empty-icon">
                  <CalendarIcon />
                </div>
                <h3>Пока нет предстоящих встреч</h3>
                <p>Как только гость запишется, встреча появится здесь.</p>
                <button className="secondary-button" type="button" onClick={onOpenGuest}>
                  Открыть страницу гостя
                  <RefreshIcon />
                </button>
              </div>
            ) : (
              <ul className="booking-list">
                {bookings.map((booking) => {
                  const dateParts = formatBookingDayParts(booking.start, timezone);
                  const isToday = isoToLocalDate(booking.start, timezone) === today;
                  return (
                    <li key={booking.id} className="booking-row">
                      <div className={`booking-date-block ${isToday ? 'today' : ''}`}>
                        <span>{dateParts.weekday}</span>
                        <strong>{dateParts.day}</strong>
                        <span>{dateParts.month}</span>
                      </div>
                      <div className="booking-main">
                        <div className="booking-title-line">
                          <strong>{booking.meetingType.name}</strong>
                          <span className="type-tag">
                            <ClockIcon />
                            {formatDurationShort(booking.meetingType.durationMinutes)}
                          </span>
                        </div>
                        <span className="booking-time">
                          {formatTimeRange(booking.start, booking.end, timezone)} · {timezoneOffset}
                        </span>
                        <div className="booking-guest">
                          <span><UserIcon /> {booking.guestName}</span>
                          <span><MailIcon /> {booking.guestEmail}</span>
                        </div>
                        {booking.notes && (
                          <p className="booking-notes">
                            <NotesIcon />
                            {booking.notes}
                          </p>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>

          <section className="card types-card">
            <div className="card-heading between">
              <div className="heading-with-icon">
                <span className="card-icon"><VideoIcon /></span>
                <div>
                  <h2>Типы встреч</h2>
                  <p>Форматы, которые видит гость на странице записи.</p>
                </div>
              </div>
              <span className="count-badge">{meetingTypes.length}</span>
            </div>

            {meetingTypes.length === 0 ? (
              <div className="empty-state compact">
                <p>Типов встреч пока нет. Создайте первый в форме слева.</p>
              </div>
            ) : (
              <ul className="type-list">
                {meetingTypes.map((type) => (
                  <li key={type.id} className="type-list-row">
                    <span className="type-list-icon"><PhoneIcon /></span>
                    <div className="type-list-main">
                      <strong>{type.name}</strong>
                      <p>{type.description || 'Без описания'}</p>
                    </div>
                    <span className="duration-chip">{formatDurationShort(type.durationMinutes)}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </div>
    </main>
  );
}
