import { expect, test } from '@playwright/test';

type SavedBooking = {
  id: string;
  meetingType: { id: string; name: string };
  guestName: string;
  guestEmail: string;
  start: string;
};

type Availability = {
  days: Array<{ slots: Array<{ start: string }> }>;
};

test('guest picks a meeting type and a slot, then books it', async ({ page, request }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Тип встречи' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Быстрый вопрос/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Консультация/ })).toBeVisible();

  const typeCard = page.getByRole('button', { name: /Знакомство/ });
  await typeCard.click();
  await expect(typeCard).toHaveAttribute('aria-pressed', 'true');

  const firstSlot = page.getByRole('button', { name: /^\d{2}:\d{2}$/ }).first();
  await expect(firstSlot).toBeVisible();
  const slotTime = (await firstSlot.textContent())?.trim() ?? '';
  expect(slotTime).toMatch(/^\d{2}:\d{2}$/);
  await firstSlot.click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'Оставьте контакты' })).toBeVisible();
  await expect(dialog.getByText('Знакомство')).toBeVisible();
  await expect(dialog).toContainText(slotTime);

  await dialog.getByLabel('Ваше имя').fill('Иван Петров');
  await dialog.getByLabel('Email').fill('ivan@example.com');
  await dialog.getByRole('button', { name: 'Записаться', exact: true }).click();

  await expect(dialog.getByRole('heading', { name: 'Вы записаны на звонок' })).toBeVisible();
  await expect(dialog.getByText('Иван Петров')).toBeVisible();
  await expect(dialog.getByText('ivan@example.com')).toBeVisible();
  await expect(page.getByRole('status')).toContainText('Запись создана');

  const bookingsResponse = await request.get('/api/bookings?includePast=false');
  expect(bookingsResponse.ok()).toBeTruthy();
  const bookings = (await bookingsResponse.json()) as SavedBooking[];
  const booking = bookings.find((item) => item.guestEmail === 'ivan@example.com');
  expect(booking).toBeDefined();
  if (!booking) {
    throw new Error('Booking was not saved by the backend');
  }
  expect(booking.guestName).toBe('Иван Петров');
  expect(booking.meetingType.name).toBe('Знакомство');

  const availabilityResponse = await request.get(
    `/api/meeting-types/${booking.meetingType.id}/availability`,
  );
  expect(availabilityResponse.ok()).toBeTruthy();
  const availability = (await availabilityResponse.json()) as Availability;
  const offeredStarts = availability.days.flatMap((day) => day.slots.map((slot) => slot.start));
  expect(offeredStarts).not.toContain(booking.start);
});
