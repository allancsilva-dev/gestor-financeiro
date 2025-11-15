import api from './api';

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
  listarPorUsuario: async (usuarioId: number) => {
    const response = await api.get(`/transacoes/usuario/${usuarioId}`);
    return response.data;
  },

  criar: async (transacao: any) => {
    const response = await api.post('/transacoes', transacao);
    return response.data;
  },

  deletar: async (id: number) => {
    await api.delete(`/transacoes/${id}`);
  }
};