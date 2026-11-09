import type {
  BadRequestCode,
  ConflictCode,
  InternalErrorCode,
  NotFoundCode,
} from '../api/generated';

/**
 * Every code the contract can return. The exhaustive map below is what makes a new
 * code in `contract/main.tsp` a TypeScript build error instead of silent data loss.
 */
export type ApiErrorCode = BadRequestCode | NotFoundCode | ConflictCode | InternalErrorCode;

export const API_ERROR_CODES: Record<ApiErrorCode, true> = {
  VALIDATION_FAILED: true,
  MALFORMED_REQUEST: true,
  INVALID_DURATION: true,
  MEETING_TYPE_NOT_FOUND: true,
  SLOT_UNAVAILABLE: true,
  SLOT_TAKEN: true,
  INTERNAL_ERROR: true,
};

export type ApiError =
  | { kind: 'http'; status: number; code: ApiErrorCode | null; message: string }
  | { kind: 'network'; message: string }
  | { kind: 'unexpected'; message: string }
  | { kind: 'aborted'; message: string };

export type ApiResult<T> = { ok: true; data: T } | { ok: false; error: ApiError };

/** One text for every transport failure, regardless of the operation. */
export const NETWORK_ERROR_MESSAGE =
  'Сервер недоступен. Проверьте соединение и попробуйте ещё раз.';
export const UNEXPECTED_ERROR_MESSAGE = 'Не удалось получить ответ сервера. Попробуйте ещё раз.';
export const ABORTED_ERROR_MESSAGE = 'Запрос отменён.';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function textField(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null;
}

export function isApiErrorCode(value: unknown): value is ApiErrorCode {
  return typeof value === 'string' && Object.hasOwn(API_ERROR_CODES, value);
}

function bodyCode(error: unknown): ApiErrorCode | null {
  if (isRecord(error) && isApiErrorCode(error.code)) {
    return error.code;
  }
  return null;
}

function bodyMessage(error: unknown, fallback: string): string {
  if (isRecord(error)) {
    return textField(error.message) ?? textField(error.detail) ?? fallback;
  }
  return fallback;
}

function isAbortError(error: unknown): boolean {
  if (!isRecord(error) && !(error instanceof Error)) {
    return false;
  }
  return (error as { name?: unknown }).name === 'AbortError';
}

function isNetworkError(error: unknown): boolean {
  if (error instanceof TypeError) {
    return true;
  }
  if (!isRecord(error) && !(error instanceof Error)) {
    return false;
  }
  if ((error as { name?: unknown }).name === 'TypeError') {
    return true;
  }
  const message = textField((error as { message?: unknown }).message) ?? '';
  return /failed to fetch|network ?error|network request failed|load failed/i.test(message);
}

/**
 * Turns whatever the generated SDK rejected with into the single error shape the UI
 * understands. The server message wins over the operation fallback; non-JSON bodies
 * keep the fallback.
 */
export function toApiError(
  error: unknown,
  options: { status?: number; fallback: string },
): ApiError {
  const { status, fallback } = options;

  if (isAbortError(error)) {
    return {
      kind: 'aborted',
      message: bodyMessage(error, ABORTED_ERROR_MESSAGE),
    };
  }

  if (typeof status === 'number') {
    return {
      kind: 'http',
      status,
      code: bodyCode(error),
      message: bodyMessage(error, fallback),
    };
  }

  if (isNetworkError(error)) {
    return { kind: 'network', message: NETWORK_ERROR_MESSAGE };
  }

  return { kind: 'unexpected', message: UNEXPECTED_ERROR_MESSAGE };
}

/** Both 409 codes mean the calendar moved; every other error keeps the calendar as is. */
export function isSlotConflict(error: ApiError): boolean {
  return error.kind === 'http' && (error.code === 'SLOT_TAKEN' || error.code === 'SLOT_UNAVAILABLE');
}

export function isAborted(error: ApiError): boolean {
  return error.kind === 'aborted';
}
