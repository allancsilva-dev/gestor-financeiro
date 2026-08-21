import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./api', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
import api from './api';
import { investimentoService } from './investimentoService';

describe('investimentoService', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(api.post).mockResolvedValue({ data: {} }); });

  it('envia operação real com conta de caixa', async () => {
    const payload = { tipo: 'COMPRA', data: '2026-07-16', quantidade: 2, precoUnitario: 10, carteiraId: 9, externa: false };
    await investimentoService.adicionarMovimentacao(3, payload);
    expect(api.post).toHaveBeenCalledWith('/investimentos/3/movimentacoes', payload, undefined);
  });

  it('envia snapshot externo explicitamente sem conta', async () => {
    const payload = { tipo: 'COMPRA', data: '2026-07-16', quantidade: 2, precoUnitario: 10, externa: true };
    await investimentoService.adicionarMovimentacao(3, payload);
    expect(api.post).toHaveBeenCalledWith('/investimentos/3/movimentacoes', payload, undefined);
  });

  it('manda Idempotency-Key quando a chave é informada (BACKLOG-0081)', async () => {
    const payload = { tipo: 'COMPRA', data: '2026-07-16', quantidade: 2, precoUnitario: 10, externa: true };
    await investimentoService.adicionarMovimentacao(3, payload, 'mov:abc');
    expect(api.post).toHaveBeenCalledWith('/investimentos/3/movimentacoes', payload, {
      headers: { 'Idempotency-Key': 'mov:abc' },
    });
  });

  it('desembrulha o envelope paginado nas listagens (BACKLOG-0082)', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { content: [{ id: 1 }] } });

    expect(await investimentoService.listar()).toEqual([{ id: 1 }]);
    expect(api.get).toHaveBeenCalledWith('/investimentos', { params: { size: 100 } });

    expect(await investimentoService.listarMovimentacoes(3)).toEqual([{ id: 1 }]);
    expect(api.get).toHaveBeenCalledWith('/investimentos/3/movimentacoes', { params: { size: 100 } });
  });

  it('trata resposta sem content como lista vazia', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: {} });
    expect(await investimentoService.listar()).toEqual([]);
  });
});
