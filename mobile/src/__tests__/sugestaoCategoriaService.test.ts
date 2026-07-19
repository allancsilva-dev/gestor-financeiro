jest.mock('../services/api', () => ({
  __esModule: true,
  default: { get: jest.fn() },
}));

import api from '../services/api';
import { transacaoService } from '../services/transacaoService';

describe('sugestão de categoria (PR-F3-02/05)', () => {
  beforeEach(() => jest.clearAllMocks());

  it('consulta o endpoint com descrição e tipo', async () => {
    const sugestao = {
      criterio: 'DESCRICAO_IGUAL',
      categoria: { id: 5, nome: 'Alimentação', cor: '#EF4444', icone: '🍔' },
    };
    (api.get as jest.Mock).mockResolvedValue({ data: sugestao });

    await expect(transacaoService.sugerirCategoria('cafe da manha', 'SAIDA'))
      .resolves.toEqual(sugestao);
    expect(api.get).toHaveBeenCalledWith('/v1/transacoes/sugestao-categoria', {
      params: { descricao: 'cafe da manha', tipo: 'SAIDA' },
    });
  });

  it('propaga NENHUMA com categoria nula sem transformar', async () => {
    (api.get as jest.Mock).mockResolvedValue({ data: { criterio: 'NENHUMA', categoria: null } });
    await expect(transacaoService.sugerirCategoria('inedita', 'ENTRADA'))
      .resolves.toEqual({ criterio: 'NENHUMA', categoria: null });
  });
});
