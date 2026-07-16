import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./api', () => ({ default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));

import api from './api';
import contaFinanceiraService, { contaPodeMovimentarCaixa, type ContaFinanceira } from './contaFinanceiraService';

const conta = (partial: Partial<ContaFinanceira> = {}): ContaFinanceira => ({
  id: 1, nome: 'Conta', saldo: 100,
  natureza: 'ATIVO', subtipo: 'CORRENTE', liquidez: 'IMEDIATA',
  origemDados: 'MANUAL', estadoConciliacao: 'CONCILIADA', moeda: 'BRL', ...partial,
});

describe('contaFinanceiraService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('usa a rota canônica e preserva paginação', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { content: [], number: 2, size: 30 } });
    await contaFinanceiraService.listar(2, 30);
    expect(api.get).toHaveBeenCalledWith('/contas-financeiras/minhas', { params: { page: 2, size: 30 } });
  });

  it('envia ajuste e consulta movimentos e reconciliação nas rotas canônicas', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: conta() });
    vi.mocked(api.get).mockResolvedValue({ data: { content: [] } });
    await contaFinanceiraService.ajustar(7, { tipo: 'SAIDA', valor: 25, descricao: 'Correção' });
    await contaFinanceiraService.listarMovimentos(7, 1, 10);
    await contaFinanceiraService.reconciliar(7);
    expect(api.post).toHaveBeenCalledWith('/contas-financeiras/7/ajustes', { tipo: 'SAIDA', valor: 25, descricao: 'Correção' });
    expect(api.get).toHaveBeenCalledWith('/contas-financeiras/7/movimentos', { params: { page: 1, size: 10 } });
    expect(api.get).toHaveBeenCalledWith('/contas-financeiras/7/reconciliacao');
  });

  it.each([
    [conta(), true],
    [conta({ natureza: 'PASSIVO', subtipo: 'CARTAO' }), false],
    [conta({ subtipo: 'COFRE' }), false],
    [conta({ subtipo: 'CUSTODIA' }), false],
    [conta({ liquidez: 'D1' }), false],
    [conta({ estadoConciliacao: 'PENDENTE' }), false],
  ])('restringe corretamente contas incompatíveis com caixa', (item, expected) => {
    expect(contaPodeMovimentarCaixa(item as ContaFinanceira)).toBe(expected);
  });
});
