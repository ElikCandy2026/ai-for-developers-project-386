// @vitest-environment jsdom
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import App from './App';
import type { Availability, Booking, MeetingType, Owner } from './data/api';
import * as api from './data/api';
import { formatTime } from './lib/format';

vi.mock('./data/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./data/api')>();
  return {
    ...actual,
    createBooking: vi.fn(),
    fetchAvailability: vi.fn(),
    fetchBootstrap: vi.fn(),
  };
});

const owner: Owner = {
  id: 'owner_anna',
  displayName: 'Анна Соколова',
  email: 'anna.sokolova@callbook.local',
  rules: {
    timezone: 'Europe/Moscow',
    workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    workdayStart: '09:00',
    workdayEnd: '18:00',
    slotStepMinutes: 30,
    bookingWindowDays: 14,
    bookingWindowStartOffsetDays: 0,
    minimumNoticeMinutes: 0,
    minimumDurationMinutes: 15,
    maximumDurationMinutes: 120,
  },
};

const introType: MeetingType = {
  id: 'type-intro',
  name: 'Знакомство',
  description: 'Короткий звонок, чтобы познакомиться.',
  durationMinutes: 30,
  createdAt: '2026-11-01T00:00:00.000Z',
};

const quickType: MeetingType = {
  id: 'type-quick',
  name: 'Быстрый вопрос',
  description: 'Пятнадцатиминутный звонок.',
  durationMinutes: 15,
  createdAt: '2026-11-01T00:00:00.000Z',
};

const meetingTypes = [introType, quickType];
const SLOT_START = '2026-11-11T07:00:00.000Z';

function slotFor(type: MeetingType) {
  const start = new Date(SLOT_START);
  const end = new Date(start.getTime() + type.durationMinutes * 60_000);
  return { start: start.toISOString(), end: end.toISOString() };
}

function availabilityFor(type: MeetingType): Availability {
  return {
    meetingType: {
      id: type.id,
      name: type.name,
      description: type.description,
      durationMinutes: type.durationMinutes,
    },
    timezone: owner.rules.timezone,
    windowStartDate: '2026-11-10',
    windowEndDate: '2026-11-23',
    slotStepMinutes: owner.rules.slotStepMinutes,
    durationMinutes: type.durationMinutes,
    days: [
      {
        date: '2026-11-11',
        weekday: 'WEDNESDAY',
        slots: [slotFor(type)],
      },
    ],
  };
}

const booking: Booking = {
  id: 'booking-1',
  meetingType: {
    id: introType.id,
    name: introType.name,
    description: introType.description,
    durationMinutes: introType.durationMinutes,
  },
  guestName: 'Иван Петров',
  guestEmail: 'ivan@example.com',
  start: slotFor(introType).start,
  end: slotFor(introType).end,
  createdAt: '2026-11-10T00:00:00.000Z',
};

describe('App guest page', () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    window.history.replaceState(null, '', '/');
    vi.mocked(api.fetchBootstrap).mockResolvedValue({
      ok: true,
      data: { owner, meetingTypes },
    });
    vi.mocked(api.fetchAvailability).mockImplementation(async (meetingTypeId) => {
      const type = meetingTypes.find((item) => item.id === meetingTypeId) ?? introType;
      return { ok: true, data: availabilityFor(type) };
    });
    vi.mocked(api.createBooking).mockResolvedValue({ ok: true, data: booking });
  });

  it('shows meeting types and opens the calendar for the selected type', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Знакомство' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Быстрый вопрос' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Тип встречи' })).toBeTruthy();

    await user.click(screen.getByRole('button', { name: /Быстрый вопрос/ }));

    await waitFor(() =>
      expect(api.fetchAvailability).toHaveBeenCalledWith('type-quick', expect.any(AbortSignal)),
    );
    expect(
      await screen.findByRole('button', {
        name: formatTime(SLOT_START, owner.rules.timezone),
      }),
    ).toBeTruthy();
  });

  it('leads from a slot to the booking form and confirms the booking', async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole('heading', { name: 'Знакомство' });
    await user.click(
      await screen.findByRole('button', {
        name: formatTime(SLOT_START, owner.rules.timezone),
      }),
    );

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('heading', { name: 'Оставьте контакты' })).toBeTruthy();

    await user.type(within(dialog).getByPlaceholderText('Как к вам обращаться'), 'Иван Петров');
    await user.type(within(dialog).getByPlaceholderText('you@example.com'), 'ivan@example.com');
    await user.click(within(dialog).getByRole('button', { name: 'Записаться' }));

    expect(await screen.findByRole('heading', { name: 'Вы записаны на звонок' })).toBeTruthy();
    expect(api.createBooking).toHaveBeenCalledWith({
      meetingTypeId: 'type-intro',
      start: slotFor(introType).start,
      guestName: 'Иван Петров',
      guestEmail: 'ivan@example.com',
      notes: undefined,
    });
  });
});
