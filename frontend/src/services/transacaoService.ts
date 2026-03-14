import api from './api';
import type { PagedResponse } from '../types';

export interface Transacao {
  id?: number;
  usuario?: { id: number };
  conta?: { id: number };
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

  criar: async (transacao: any) => {
    const response = await api.post('/transacoes', transacao);
    return response.data;
  },

  atualizar: async (id: number, transacao: any) => {
    const response = await api.put(`/transacoes/${id}`, transacao);
    return response.data;
  },

  deletar: async (id: number) => {
    await api.delete(`/transacoes/${id}`);
  }
};