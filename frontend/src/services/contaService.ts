import api from './api';
import type { PagedResponse } from '../types';

export interface Conta {
  id?: number;
  nome: string;
  tipo: 'CREDITO' | 'DEBITO' | 'DINHEIRO' | 'POUPANCA';
  limiteTotal?: number;
  valorGasto?: number;
  saldoAtual?: number;
  diaFechamento?: number;
  diaVencimento?: number;
  cor?: string;
  ativo?: boolean;
}

export const contaService = {
  listarPorUsuario: async (_usuarioId: number, page = 0, size = 20) => {
    const response = await api.get<PagedResponse<Conta>>('/contas/minhas', {
      params: { page, size },
    });
    return response.data.content ?? [];
  },

  listarPorUsuarioPaginado: async (page = 0, size = 20) => {
    const response = await api.get<PagedResponse<Conta>>('/contas/minhas', {
      params: { page, size },
    });
    return response.data;
  },

  criar: async (conta: Conta) => {
    const response = await api.post('/contas', conta);
    return response.data;
  },

  atualizar: async (id: number, conta: Conta) => {
    const response = await api.put(`/contas/${id}`, conta);
    return response.data;
  },

  deletar: async (id: number) => {
    await api.delete(`/contas/${id}`);
  }
};