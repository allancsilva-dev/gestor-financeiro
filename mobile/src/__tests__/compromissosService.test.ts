jest.mock('../services/api', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

import api from '../services/api';
import compromissosService from '../services/compromissosService';
import { formatDateOnlyBR } from '../utils/format';

describe('compromissos próximos (PR-F3-01/07)', () => {
  beforeEach(() => jest.clearAllMocks());

  it('consome o contrato com grupos COMPROMETIDO e PREVISTO', async () => {
    const resposta = {
      referencia: '2026-07-19',
      horizonte: '2026-07-31',
      totalComprometido: 600,
      itens: [
        { tipo: 'FATURA', id: 1, descricao: 'Fatura 7/2026', valor: 400, vencimento: '2026-07-25', grupo: 'COMPROMETIDO', alerta: null },
        { tipo: 'CONTA_FIXA', id: 2, descricao: 'Aluguel', valor: 800, vencimento: '2026-07-28', grupo: 'PREVISTO', alerta: 'FALHA_SALDO' },
      ],
    };
    (api.get as jest.Mock).mockResolvedValue({ data: resposta });

    await expect(compromissosService.listar()).resolves.toEqual(resposta);
    expect(api.get).toHaveBeenCalledWith('/v1/compromissos', { params: undefined });
  });

  it('envia o horizonte quando informado', async () => {
    (api.get as jest.Mock).mockResolvedValue({ data: { itens: [] } });
    await compromissosService.listar('2026-09-30');
    expect(api.get).toHaveBeenCalledWith('/v1/compromissos', { params: { ate: '2026-09-30' } });
  });
});

describe('formatDateOnlyBR', () => {
  it('formata vencimento sem deslocamento de fuso', () => {
    expect(formatDateOnlyBR('2026-07-31')).toBe('31/07/2026');
    expect(formatDateOnlyBR('2026-01-01')).toBe('01/01/2026');
  });
});
