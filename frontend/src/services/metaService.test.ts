import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('./api', () => ({ default: { get: vi.fn() } }));

import api from './api';
import { metaService } from './metaService';

describe('metaService', () => {
  beforeEach(() => vi.clearAllMocks());

  it.each(['ATIVA', 'CONCLUIDA', 'ARQUIVADA'] as const)('envia o filtro %s', async (status) => {
    vi.mocked(api.get).mockResolvedValue({ data: { content: [], totalPages: 0 } });
    await metaService.listarPorUsuarioPaginado(0, 20, undefined, status);
    expect(api.get).toHaveBeenCalledWith('/metas/minhas', expect.objectContaining({ params: { page: 0, size: 20, status } }));
  });
});
