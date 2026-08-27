jest.mock('../services/api', () => ({ __esModule: true, default: { get: jest.fn(), put: jest.fn() } }));

import api from '../services/api';
import { metaService } from '../services/metaService';
import { StatusMeta } from '../types';

describe('metaService', () => {
  beforeEach(() => jest.clearAllMocks());

  it.each<StatusMeta>(['ATIVA', 'CONCLUIDA', 'ARQUIVADA'])('envia o filtro %s', async (status) => {
    (api.get as jest.Mock).mockResolvedValue({ data: { content: [], totalPages: 0 } });
    await metaService.listar(status);
    expect(api.get).toHaveBeenCalledWith(`/v1/metas/minhas?page=0&size=20&status=${status}`);
  });

  it('liga o aporte automático com dia e conta de origem', async () => {
    (api.put as jest.Mock).mockResolvedValue({ data: { id: 1 } });

    await metaService.configurarAporteAutomatico(1, { ativo: true, dia: 5, carteiraId: 9 });

    expect(api.put).toHaveBeenCalledWith('/v1/metas/1/aporte-automatico',
      { ativo: true, dia: 5, carteiraId: 9 });
  });

  it('desligar não exige informar conta nem dia', async () => {
    (api.put as jest.Mock).mockResolvedValue({ data: { id: 1 } });

    await metaService.configurarAporteAutomatico(1, { ativo: false });

    expect(api.put).toHaveBeenCalledWith('/v1/metas/1/aporte-automatico', { ativo: false });
  });
});
