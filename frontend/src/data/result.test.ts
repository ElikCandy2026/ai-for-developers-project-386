import { describe, expect, it } from 'vitest';

import {
  isAborted,
  isSlotConflict,
  NETWORK_ERROR_MESSAGE,
  UNEXPECTED_ERROR_MESSAGE,
  toApiError,
} from './result';

const fallback = 'Не удалось выполнить запрос.';

describe('toApiError', () => {
  it('maps a known error code from an HTTP response', () => {
    const error = toApiError(
      { code: 'SLOT_TAKEN', message: 'Слот уже занят.' },
      { status: 409, fallback },
    );

    expect(error).toEqual({
      kind: 'http',
      status: 409,
      code: 'SLOT_TAKEN',
      message: 'Слот уже занят.',
    });
  });

  it('keeps an unknown error code as null and still uses the server message', () => {
    const error = toApiError(
      { code: 'SOMETHING_NEW', message: 'Новый код из контракта.' },
      { status: 400, fallback },
    );

    expect(error).toMatchObject({
      kind: 'http',
      status: 400,
      code: null,
      message: 'Новый код из контракта.',
    });
  });

  it('falls back to the operation message for a non-JSON body', () => {
    const error = toApiError('Internal Server Error', { status: 500, fallback });

    expect(error).toEqual({
      kind: 'http',
      status: 500,
      code: null,
      message: fallback,
    });
  });

  it('prefers message over detail', () => {
    const error = toApiError(
      { code: 'VALIDATION_FAILED', message: 'Сообщение', detail: 'Подробность' },
      { status: 400, fallback },
    );

    expect(error).toMatchObject({ message: 'Сообщение' });
  });

  it('uses detail when the server does not send a message', () => {
    const error = toApiError(
      { code: 'VALIDATION_FAILED', detail: 'Подробность' },
      { status: 400, fallback },
    );

    expect(error).toMatchObject({ message: 'Подробность' });
  });

  it('uses the fallback when the body has neither message nor detail', () => {
    const error = toApiError({ code: 'INTERNAL_ERROR' }, { status: 500, fallback });

    expect(error).toMatchObject({ kind: 'http', message: fallback });
  });

  it('maps transport failures to a network error with one shared text', () => {
    const error = toApiError(new TypeError('Failed to fetch'), { fallback });

    expect(error).toEqual({ kind: 'network', message: NETWORK_ERROR_MESSAGE });
  });

  it('maps an aborted request to an aborted error', () => {
    const error = toApiError(new DOMException('Запрос отменён.', 'AbortError'), { fallback });

    expect(error).toEqual({ kind: 'aborted', message: 'Запрос отменён.' });
  });

  it('maps an unrecognised thrown value to an unexpected error', () => {
    const error = toApiError({}, { fallback });

    expect(error).toEqual({ kind: 'unexpected', message: UNEXPECTED_ERROR_MESSAGE });
  });
});

describe('predicates', () => {
  it('treats both 409 codes as a slot conflict', () => {
    const taken = toApiError({ code: 'SLOT_TAKEN', message: 'Занято.' }, { status: 409, fallback });
    const unavailable = toApiError(
      { code: 'SLOT_UNAVAILABLE', message: 'Недоступно.' },
      { status: 409, fallback },
    );

    expect(isSlotConflict(taken)).toBe(true);
    expect(isSlotConflict(unavailable)).toBe(true);
  });

  it('does not treat validation or unknown errors as a slot conflict', () => {
    const validation = toApiError(
      { code: 'VALIDATION_FAILED', message: 'Имя пустое.' },
      { status: 400, fallback },
    );
    const unknown = toApiError({ code: 'NEW_CODE' }, { status: 409, fallback });

    expect(isSlotConflict(validation)).toBe(false);
    expect(isSlotConflict(unknown)).toBe(false);
  });

  it('detects aborted errors only', () => {
    const aborted = toApiError(new DOMException('Отмена.', 'AbortError'), { fallback });
    const network = toApiError(new TypeError('Failed to fetch'), { fallback });

    expect(isAborted(aborted)).toBe(true);
    expect(isAborted(network)).toBe(false);
    expect(isSlotConflict(aborted)).toBe(false);
  });
});
