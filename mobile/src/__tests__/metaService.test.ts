jest.mock('../services/api', () => ({ __esModule: true, default: { get: jest.fn() } }));

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
});
