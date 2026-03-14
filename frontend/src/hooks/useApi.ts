import { useCallback, useEffect, useRef, useState } from 'react';
import type { DependencyList } from 'react';

type ApiExecutor<T> = (signal: AbortSignal) => Promise<T>;

interface RetryOptions {
  retries?: number;
  initialDelayMs?: number;
  enabled?: boolean;
}

interface UseApiOptions {
  immediate?: boolean;
  deps?: DependencyList;
  retry?: RetryOptions;
}

const defaultRetry: Required<RetryOptions> = {
  retries: 3,
  initialDelayMs: 1000,
  enabled: true,
};

const MAX_RETRIES = 3;

const createAbortError = () => {
  const err = new Error('Request aborted');
  err.name = 'AbortError';
  return err;
};

const sleepWithSignal = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(createAbortError());
      return;
    }

    const timeoutId = setTimeout(() => {
      signal.removeEventListener('abort', onAbort);
      resolve();
    }, ms);

    const onAbort = () => {
      clearTimeout(timeoutId);
      signal.removeEventListener('abort', onAbort);
      reject(createAbortError());
    };

    signal.addEventListener('abort', onAbort, { once: true });
  });

const isAbortError = (error: unknown): boolean => {
  const e = error as { name?: string; code?: string };
  return e?.name === 'AbortError' || e?.code === 'ERR_CANCELED';
};

const isRetryableError = (error: unknown): boolean => {
  const e = error as {
    code?: string;
    response?: { status?: number };
  };

  if (isAbortError(error)) {
    return false;
  }

  if (typeof e?.response?.status === 'number') {
    return e.response.status >= 500;
  }

  return e?.code === 'ERR_NETWORK' || e?.code === 'ECONNABORTED' || !e?.response;
};

export async function fetchWithRetry<T>(
  operation: () => Promise<T>,
  signal: AbortSignal,
  retry?: RetryOptions
): Promise<T> {
  const config = { ...defaultRetry, ...retry };
  const retries = Math.max(0, Math.min(config.retries, MAX_RETRIES));

  let attempt = 0;
  while (true) {
    if (signal.aborted) {
      throw createAbortError();
    }

    try {
      return await operation();
    } catch (error) {
      attempt += 1;
      const shouldRetry = config.enabled && attempt <= retries && isRetryableError(error);

      if (!shouldRetry) {
        throw error;
      }

      const backoffMs = config.initialDelayMs * Math.pow(2, attempt - 1);
      await sleepWithSignal(backoffMs, signal);
    }
  }
}

export function useApi<T>(executor: ApiExecutor<T>, options: UseApiOptions = {}) {
  const { immediate = true, deps = [], retry } = options;

  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(immediate);
  const [error, setError] = useState<unknown>(null);
  const [failed, setFailed] = useState<boolean>(false);

  const executorRef = useRef(executor);
  const retryRef = useRef(retry);
  const mountedRef = useRef(true);

  executorRef.current = executor;
  retryRef.current = retry;

  const controllerRef = useRef<AbortController | null>(null);

  const run = useCallback(async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    if (mountedRef.current) {
      setLoading(true);
      setError(null);
      setFailed(false);
    }

    try {
      const result = await fetchWithRetry(
        () => executorRef.current(controller.signal),
        controller.signal,
        retryRef.current
      );
      if (!controller.signal.aborted && mountedRef.current) {
        setData(result);
      }
    } catch (err) {
      if (!isAbortError(err) && mountedRef.current) {
        setError(err);
        setFailed(true);
      }
    } finally {
      if (!controller.signal.aborted && mountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;

    if (immediate) {
      run();
    }

    return () => {
      mountedRef.current = false;
      controllerRef.current?.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [immediate, ...deps]);

  const refetch = useCallback(async () => {
    await run();
  }, [run]);

  return { data, loading, error, failed, refetch, setData };
}
