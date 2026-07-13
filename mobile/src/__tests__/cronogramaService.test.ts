jest.mock('../services/api', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

import api from '../services/api';
import { transacaoService } from '../services/transacaoService';

it('consome contrato canônico do cronograma', async () => {
  const itens = [{ id: 1, origem: 'CARTAO', numero: 1, total: 2, valor: 50,
    vencimento: '2026-08-05', status: 'PENDENTE' }];
  (api.get as jest.Mock).mockResolvedValue({ data: itens });
  await expect(transacaoService.cronograma(42)).resolves.toEqual(itens);
  expect(api.get).toHaveBeenCalledWith('/v1/transacoes/42/cronograma');
});
