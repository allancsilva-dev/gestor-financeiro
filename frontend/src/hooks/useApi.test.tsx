import { renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { fetchWithRetry, useApi } from './useApi';

describe('fetchWithRetry', () => {
  it('deve tentar novamente em erro de rede e retornar sucesso', async () => {
    let attempts = 0;

    const result = await fetchWithRetry(
      async () => {
        attempts += 1;
        if (attempts < 3) {
          throw { code: 'ERR_NETWORK' };
        }
        return 'ok';
      },
      new AbortController().signal,
      { retries: 3, initialDelayMs: 1, enabled: true }
    );

    expect(result).toBe('ok');
    expect(attempts).toBe(3);
  });

  it('deve falhar sem retry para erro 4xx', async () => {
    await expect(
      fetchWithRetry(
        async () => {
          throw { response: { status: 400 } };
        },
        new AbortController().signal,
        { retries: 3, initialDelayMs: 1, enabled: true }
      )
    ).rejects.toBeTruthy();
  });
});

describe('useApi', () => {
  it('deve executar imediatamente e popular data', async () => {
    const executor = vi.fn(async () => ({ total: 10 }));

    const { result } = renderHook(() => useApi(executor, { immediate: true }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    expect(executor).toHaveBeenCalledTimes(1);
    expect(result.current.data).toEqual({ total: 10 });
    expect(result.current.error).toBeNull();
  });

  it('deve executar apenas no refetch quando immediate=false', async () => {
    const executor = vi.fn(async () => 'dados');

    const { result } = renderHook(() => useApi(executor, { immediate: false }));

    expect(executor).not.toHaveBeenCalled();
    expect(result.current.loading).toBe(false);

    await result.current.refetch();

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await waitFor(() => {
      expect(result.current.data).toBe('dados');
    });

    expect(executor).toHaveBeenCalledTimes(1);
  });
});
