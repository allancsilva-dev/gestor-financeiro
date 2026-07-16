import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./api', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
import api from './api';
import { investimentoService } from './investimentoService';

describe('investimentoService', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(api.post).mockResolvedValue({ data: {} }); });

  it('envia operação real com conta de caixa', async () => {
    const payload = { tipo: 'COMPRA', data: '2026-07-16', quantidade: 2, precoUnitario: 10, carteiraId: 9, externa: false };
    await investimentoService.adicionarMovimentacao(3, payload);
    expect(api.post).toHaveBeenCalledWith('/investimentos/3/movimentacoes', payload);
  });

  it('envia snapshot externo explicitamente sem conta', async () => {
    const payload = { tipo: 'COMPRA', data: '2026-07-16', quantidade: 2, precoUnitario: 10, externa: true };
    await investimentoService.adicionarMovimentacao(3, payload);
    expect(api.post).toHaveBeenCalledWith('/investimentos/3/movimentacoes', payload);
  });
});
