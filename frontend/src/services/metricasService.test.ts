import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./api', () => ({ default: { get: vi.fn() } }));
import api from './api';
import metricasService from './metricasService';

describe('metricasService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('consulta métricas e drill-down oficiais', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] });
    const signal = new AbortController().signal;
    await metricasService.obter(signal);
    await metricasService.listarOrigens('DISPONIVEL_PARA_GASTAR', signal);
    expect(api.get).toHaveBeenNthCalledWith(1, '/metricas', { signal });
    expect(api.get).toHaveBeenNthCalledWith(2, '/metricas/DISPONIVEL_PARA_GASTAR/origens', { signal });
  });
});
