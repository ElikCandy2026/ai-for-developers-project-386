import { useCallback, useEffect, useRef, useState } from 'react';

import { isAborted, UNEXPECTED_ERROR_MESSAGE } from './result';
import type { ApiError, ApiResult } from './result';

export type Resource<T> = {
  data: T | null;
  loading: boolean;
  error: ApiError | null;
  /** Re-runs the loader, cancelling the request that is still in flight. */
  reload: () => void;
  /** Local-only update, e.g. appending a meeting type that was just created. */
  setData: (update: T | null | ((current: T | null) => T | null)) => void;
};

type ResourceOptions = {
  /** When false the loader is not called and the resource stays empty. */
  enabled?: boolean;
};

/**
 * Owns the fetch lifecycle shared by every screen: loading/error state, an
 * `AbortController` per run, a stale-response guard, and a manual `reload`.
 * Aborted runs are expected churn, not errors, so they keep the state untouched.
 */
export function useResource<T>(
  loader: (signal: AbortSignal) => Promise<ApiResult<T>>,
  deps: readonly unknown[],
  { enabled = true }: ResourceOptions = {},
): Resource<T> {
  const [data, setDataState] = useState<T | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<ApiError | null>(null);
  const [version, setVersion] = useState(0);

  const loaderRef = useRef(loader);
  loaderRef.current = loader;
  const generationRef = useRef(0);

  const reload = useCallback(() => setVersion((current) => current + 1), []);

  const setData = useCallback(
    (update: T | null | ((current: T | null) => T | null)) => {
      setDataState((current) =>
        typeof update === 'function'
          ? (update as (current: T | null) => T | null)(current)
          : update,
      );
    },
    [],
  );

  useEffect(() => {
    const generation = ++generationRef.current;

    if (!enabled) {
      setDataState(null);
      setError(null);
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    setDataState(null);
    setError(null);
    setLoading(true);

    let pending: Promise<ApiResult<T>>;
    try {
      pending = loaderRef.current(controller.signal);
    } catch {
      setError({ kind: 'unexpected', message: UNEXPECTED_ERROR_MESSAGE });
      setLoading(false);
      return () => controller.abort();
    }

    void pending.then(
      (result) => {
        if (generation !== generationRef.current) {
          return;
        }
        if (result.ok) {
          setDataState(result.data);
        } else if (!isAborted(result.error)) {
          setError(result.error);
        }
        setLoading(false);
      },
      () => {
        if (generation !== generationRef.current) {
          return;
        }
        setError({ kind: 'unexpected', message: UNEXPECTED_ERROR_MESSAGE });
        setLoading(false);
      },
    );

    return () => {
      controller.abort();
      generationRef.current += 1;
    };
    // `deps` is the caller-owned dependency list of the loader.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, version, ...deps]);

  return { data, loading, error, reload, setData };
}
