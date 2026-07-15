import api from './api';
import type { PagedResponse } from '../types';

export type StatusMeta = 'ATIVA' | 'CONCLUIDA' | 'ARQUIVADA';

export interface Meta {
  id?: number;
  nome: string;
  valorTotal: number;
  valorReservado?: number;
  valorMensal: number;
  dataInicio?: string;
  dataPrevista?: string;
  dataConclusao?: string;
  cor?: string;
  icone?: string;
  descricao?: string;
  status?: StatusMeta;
  /** @deprecated use status */
  ativa?: boolean;
}

export const metaService = {
  // ausência de status equivale a ATIVA no backend
  listarPorUsuario: async (_usuarioId: number, page = 0, size = 20, signal?: AbortSignal, status?: StatusMeta) => {
    const response = await api.get<PagedResponse<Meta>>('/metas/minhas', {
      params: { page, size, ...(status ? { status } : {}) },
      signal,
    });
    return response.data.content ?? [];
  },

  listarPorUsuarioPaginado: async (page = 0, size = 20, signal?: AbortSignal, status?: StatusMeta) => {
    const response = await api.get<PagedResponse<Meta>>('/metas/minhas', {
      params: { page, size, ...(status ? { status } : {}) },
      signal,
    });
    return response.data;
  },

  criar: async (meta: Omit<Meta, 'id'>) => {
    const response = await api.post('/metas', meta);
    return response.data;
  },

  atualizar: async (id: number, meta: Partial<Meta>) => {
    const response = await api.put(`/metas/${id}`, meta);
    return response.data;
  },

  // carteiraId obrigatório: reserva debita a carteira, resgate credita (ledger)
  adicionarValor: async (id: number, valor: number, carteiraId: number) => {
    const response = await api.put(`/metas/${id}/adicionar`, { valor, carteiraId });
    return response.data;
  },

  removerValor: async (id: number, valor: number, carteiraId: number) => {
    const response = await api.put(`/metas/${id}/remover`, { valor, carteiraId });
    return response.data;
  },

  deletar: async (id: number) => {
    await api.delete(`/metas/${id}`);
  }
};