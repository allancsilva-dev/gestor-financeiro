import api from './api';

export interface Meta {
  id?: number;
  nome: string;
  valorTotal: number;
  valorReservado?: number;
  valorMensal: number;
  dataInicio?: string;
  dataPrevista?: string;
  cor?: string;
  icone?: string;
  descricao?: string;
  ativa?: boolean;
}

export const metaService = {
  listarPorUsuario: async (usuarioId: number) => {
    const response = await api.get(`/metas/usuario/${usuarioId}`);
    return response.data;
  },

  criar: async (meta: any) => {
    const response = await api.post('/metas', meta);
    return response.data;
  },

  adicionarValor: async (id: number, valor: number) => {
    const response = await api.put(`/metas/${id}/adicionar`, { valor });
    return response.data;
  },

  removerValor: async (id: number, valor: number) => {
    const response = await api.put(`/metas/${id}/remover`, { valor });
    return response.data;
  },

  deletar: async (id: number) => {
    await api.delete(`/metas/${id}`);
  }
};