import type {
  Availability,
  Booking,
  CreateBookingRequest,
  CreateMeetingTypeRequest,
  MeetingType,
  Owner,
} from '../api/generated';
import {
  createBooking as sdkCreateBooking,
  createMeetingType as sdkCreateMeetingType,
  getAvailability,
  getOwner,
  listBookings,
  listMeetingTypes,
} from '../api/generated';
import { toApiError } from './result';
import type { ApiResult } from './result';

export type {
  Availability,
  AvailabilityDay,
  BadRequestCode,
  Booking,
  BookingRules,
  ConflictCode,
  CreateBookingRequest,
  CreateMeetingTypeRequest,
  DayOfWeek,
  InternalErrorCode,
  MeetingType,
  MeetingTypeSummary,
  NotFoundCode,
  Owner,
  Slot,
} from '../api/generated';
export {
  ABORTED_ERROR_MESSAGE,
  API_ERROR_CODES,
  isAborted,
  isApiErrorCode,
  isSlotConflict,
  NETWORK_ERROR_MESSAGE,
  toApiError,
  UNEXPECTED_ERROR_MESSAGE,
} from './result';
export type { ApiError, ApiErrorCode, ApiResult } from './result';

export type BootstrapData = {
  owner: Owner;
  meetingTypes: MeetingType[];
};

const FALLBACK = {
  owner: 'Не удалось загрузить данные владельца.',
  meetingTypes: 'Не удалось загрузить типы встреч.',
  availability: 'Не удалось загрузить свободные слоты.',
  bookings: 'Не удалось загрузить записи.',
  createMeetingType: 'Не удалось создать тип встречи.',
  createBooking: 'Не удалось создать запись. Попробуйте ещё раз.',
} as const;

type SdkResult<T> = {
  data?: T;
  error?: unknown;
  response?: Response;
};

/**
 * The generated SDK reports failures in `error`/`response` instead of throwing, so
 * every facade call funnels through here. Nothing escapes as an exception.
 */
async function run<T>(
  request: () => Promise<SdkResult<T>>,
  fallback: string,
): Promise<ApiResult<T>> {
  try {
    const result = await request();
    if (result.error !== undefined) {
      return {
        ok: false,
        error: toApiError(result.error, { status: result.response?.status, fallback }),
      };
    }
    if (result.data === undefined) {
      return { ok: false, error: toApiError(undefined, { fallback }) };
    }
    return { ok: true, data: result.data };
  } catch (error) {
    return { ok: false, error: toApiError(error, { fallback }) };
  }
}

export function fetchOwner(signal?: AbortSignal): Promise<ApiResult<Owner>> {
  return run(() => getOwner({ signal }), FALLBACK.owner);
}

export function fetchMeetingTypes(signal?: AbortSignal): Promise<ApiResult<MeetingType[]>> {
  return run(() => listMeetingTypes({ signal }), FALLBACK.meetingTypes);
}

/** The app shell always needs both halves, so one resource owns the pair. */
export async function fetchBootstrap(signal?: AbortSignal): Promise<ApiResult<BootstrapData>> {
  const [owner, meetingTypes] = await Promise.all([
    fetchOwner(signal),
    fetchMeetingTypes(signal),
  ]);
  if (!owner.ok) {
    return owner;
  }
  if (!meetingTypes.ok) {
    return meetingTypes;
  }
  return { ok: true, data: { owner: owner.data, meetingTypes: meetingTypes.data } };
}

export function fetchAvailability(
  meetingTypeId: string,
  signal?: AbortSignal,
): Promise<ApiResult<Availability>> {
  return run(
    () => getAvailability({ path: { meetingTypeId }, signal }),
    FALLBACK.availability,
  );
}

export function fetchUpcomingBookings(signal?: AbortSignal): Promise<ApiResult<Booking[]>> {
  return run(
    () => listBookings({ query: { includePast: false }, signal }),
    FALLBACK.bookings,
  );
}

export function createMeetingType(
  body: CreateMeetingTypeRequest,
): Promise<ApiResult<MeetingType>> {
  return run(() => sdkCreateMeetingType({ body }), FALLBACK.createMeetingType);
}

export function createBooking(body: CreateBookingRequest): Promise<ApiResult<Booking>> {
  return run(() => sdkCreateBooking({ body }), FALLBACK.createBooking);
}
