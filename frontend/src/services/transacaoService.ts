import api from './api';
import type { PagedResponse } from '../types';

export interface Transacao {
  id?: number;
  usuario?: { id: number };
  cartao?: { id: number; nome: string };
  cartaoId?: number;
  categoria?: { id: number };
  descricao: string;
  valorTotal: number;
  tipo: 'ENTRADA' | 'SAIDA';
  data: string;
  parcelado?: boolean;
  totalParcelas?: number;
  valorParcela?: number;
  observacoes?: string;
  status?: string;
}

export interface FiltroPeriodo {
  inicio: string;
  fim: string;
  tipo?: string;
  q?: string;
  categoriaId?: string;
  carteiraId?: string;
  cartaoId?: string;
}

export const transacaoService = {
  listarPorUsuario: async (_usuarioId: number, page = 0, size = 20) => {
    const response = await api.get<PagedResponse<Transacao>>('/transacoes/minhas', {
      params: { page, size },
    });
    return response.data.content ?? [];
  },

  listarPorUsuarioPaginado: async (page = 0, size = 20) => {
    const response = await api.get<PagedResponse<Transacao>>('/transacoes/minhas', {
      params: { page, size },
    });
    return response.data;
  },

  buscarPorId: async (id: number) => {
    const response = await api.get<Transacao>(`/transacoes/${id}`);
    return response.data;
  },

  // Filtros do contrato de drill-down (PR-F3-04), combináveis com período/tipo/busca
  listarPorPeriodo: async (filtros: FiltroPeriodo, page = 0, size = 20) => {
    const response = await api.get<PagedResponse<Transacao>>('/transacoes/periodo', {
      params: { ...filtros, page, size },
    });
    return response.data;
  },

  criar: async (transacao: Omit<Transacao, 'id'>) => {
    const response = await api.post('/transacoes', transacao);
    return response.data;
  },

  atualizar: async (id: number, transacao: Partial<Transacao>) => {
    const response = await api.put(`/transacoes/${id}`, transacao);
    return response.data;
  },

  deletar: async (id: number) => {
    await api.delete(`/transacoes/${id}`);
  }
};
