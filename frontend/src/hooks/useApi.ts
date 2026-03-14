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

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

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

  let attempt = 0;
  while (true) {
    if (signal.aborted) {
      throw new DOMException('Request aborted', 'AbortError');
    }

    try {
      return await operation();
    } catch (error) {
      attempt += 1;
      const shouldRetry = config.enabled && attempt <= config.retries && isRetryableError(error);

      if (!shouldRetry) {
        throw error;
      }

      const backoffMs = config.initialDelayMs * Math.pow(2, attempt - 1);
      await sleep(backoffMs);
    }
  }
}

export function useApi<T>(executor: ApiExecutor<T>, options: UseApiOptions = {}) {
  const { immediate = true, deps = [], retry } = options;

  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(immediate);
  const [error, setError] = useState<unknown>(null);
  const controllerRef = useRef<AbortController | null>(null);

  const run = useCallback(async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    setLoading(true);
    setError(null);

    try {
      const result = await fetchWithRetry(() => executor(controller.signal), controller.signal, retry);
      if (!controller.signal.aborted) {
        setData(result);
      }
    } catch (err) {
      if (!isAbortError(err)) {
        setError(err);
      }
    } finally {
      if (!controller.signal.aborted) {
        setLoading(false);
      }
    }
  }, [executor, retry]);

  useEffect(() => {
    if (immediate) {
      run();
    }

    return () => {
      controllerRef.current?.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [immediate, run, ...deps]);

  const refetch = useCallback(async () => {
    await run();
  }, [run]);

  return { data, loading, error, refetch, setData };
}
