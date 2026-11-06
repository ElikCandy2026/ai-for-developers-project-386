import { useEffect, useId, useMemo, useState, type FormEvent } from 'react';

import type {
  Booking,
  CreateBookingRequest,
  MeetingType,
  Owner,
  Slot,
} from '../api/generated';
import { createBooking } from '../api/generated';
import { apiErrorMessage } from '../lib/apiError';
import {
  formatBookingDate,
  formatDuration,
  formatLocalDateLong,
  formatTime,
  formatTimezoneOffset,
  formatTimeRange,
} from '../lib/format';
import { AlertIcon, CloseIcon, ClockIcon, MailIcon, NotesIcon, UserIcon, VideoIcon } from './Icons';

type BookingDialogProps = {
  owner: Owner;
  meetingType: MeetingType;
  slot: Slot;
  date: string;
  onClose: () => void;
  onBooked: (booking: Booking) => void;
  onSlotUnavailable: () => void;
};

export function BookingDialog({
  owner,
  meetingType,
  slot,
  date,
  onClose,
  onBooked,
  onSlotUnavailable,
}: BookingDialogProps) {
  const titleId = useId();
  const [guestName, setGuestName] = useState('');
  const [guestEmail, setGuestEmail] = useState('');
  const [notes, setNotes] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [booking, setBooking] = useState<Booking | null>(null);

  const timezone = owner.rules.timezone;
  const timezoneOffset = useMemo(() => formatTimezoneOffset(timezone), [timezone]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !pending) {
        onClose();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [onClose, pending]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) {
      return;
    }
    setError(null);
    setPending(true);

    const body: CreateBookingRequest = {
      meetingTypeId: meetingType.id,
      start: slot.start,
      guestName: guestName.trim(),
      guestEmail: guestEmail.trim(),
      notes: notes.trim() || undefined,
    };

    try {
      const result = await createBooking({ body });
      if (result.error) {
        const message = apiErrorMessage(result.error, 'Не удалось создать запись. Попробуйте ещё раз.');
        setError(message);
        onSlotUnavailable();
        return;
      }
      if (result.data) {
        setBooking(result.data);
        onBooked(result.data);
      }
    } catch {
      setError('Сервер недоступен. Проверьте соединение и попробуйте ещё раз.');
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => e.target === e.currentTarget && !pending && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <button className="icon-button modal-close" type="button" onClick={onClose} aria-label="Закрыть" disabled={pending}>
          <CloseIcon />
        </button>

        {booking ? (
          <div className="success-view">
            <div className="success-mark">
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="m4 12.5 5 5L20 6.5" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </div>
            <p className="eyebrow">Готово</p>
            <h2 id={titleId}>Вы записаны на звонок</h2>
            <p className="muted success-lead">
              Встреча появилась в календаре владельца. Мы не отправляем письма — сохраните детали сами.
            </p>

            <div className="success-ticket">
              <div className="ticket-main">
                <span className="ticket-label">{booking.meetingType.name}</span>
                <strong>
                  {formatBookingDate(booking.start, timezone)}, {formatTime(booking.start, timezone)}
                  {' – '}
                  {formatTime(booking.end, timezone)}
                </strong>
                <span className="muted">{formatDuration(booking.meetingType.durationMinutes)} · {timezone} ({timezoneOffset})</span>
              </div>
              <div className="ticket-guest">
                <span className="ticket-label">Гость</span>
                <strong>{booking.guestName}</strong>
                <span className="muted">{booking.guestEmail}</span>
              </div>
            </div>

            <button className="primary-button full-width" type="button" onClick={onClose}>
              Записаться ещё раз
            </button>
          </div>
        ) : (
          <>
            <p className="eyebrow">Подтверждение записи</p>
            <h2 id={titleId}>Оставьте контакты</h2>

            <div className="dialog-summary">
              <div className="dialog-summary-icon">
                <VideoIcon />
              </div>
              <div>
                <strong>{meetingType.name}</strong>
                <p>{formatLocalDateLong(date)}</p>
                <span>
                  {formatTimeRange(slot.start, slot.end, timezone)} · {formatDuration(meetingType.durationMinutes)}
                </span>
              </div>
              <span className="timezone-note">{timezoneOffset}</span>
            </div>

            <form className="booking-form" onSubmit={handleSubmit}>
              <label className="form-field">
                <span>Ваше имя</span>
                <div className="input-wrap">
                  <UserIcon />
                  <input
                    className="input with-icon"
                    name="guestName"
                    value={guestName}
                    onChange={(event) => setGuestName(event.target.value)}
                    placeholder="Как к вам обращаться"
                    autoComplete="name"
                    required
                    maxLength={120}
                    autoFocus
                  />
                </div>
              </label>

              <label className="form-field">
                <span>Email</span>
                <div className="input-wrap">
                  <MailIcon />
                  <input
                    className="input with-icon"
                    name="guestEmail"
                    type="email"
                    value={guestEmail}
                    onChange={(event) => setGuestEmail(event.target.value)}
                    placeholder="you@example.com"
                    autoComplete="email"
                    required
                    maxLength={254}
                  />
                </div>
              </label>

              <label className="form-field">
                <span>Комментарий <em>необязательно</em></span>
                <div className="input-wrap textarea-wrap">
                  <NotesIcon />
                  <textarea
                    className="input textarea with-icon"
                    name="notes"
                    value={notes}
                    onChange={(event) => setNotes(event.target.value)}
                    placeholder="О чём хотите поговорить?"
                    rows={3}
                    maxLength={1000}
                  />
                </div>
              </label>

              <p className="fine-print">
                <ClockIcon />
                Время указано по часовому поясу владельца: {timezone} ({timezoneOffset}).
              </p>

              {error && (
                <div className="alert alert-error" role="alert">
                  <AlertIcon />
                  <span>{error}</span>
                </div>
              )}

              <div className="dialog-actions">
                <button className="secondary-button" type="button" onClick={onClose} disabled={pending}>
                  Отмена
                </button>
                <button className="primary-button" type="submit" disabled={pending}>
                  {pending ? <span className="spinner" /> : 'Записаться'}
                </button>
              </div>
            </form>
          </>
        )}
      </section>
    </div>
  );
}
