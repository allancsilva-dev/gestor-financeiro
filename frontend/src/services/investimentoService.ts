import api from './api';

export interface Ativo {
  id?: number;
  ticker: string;
  nome: string;
  tipo: string;
  quantidade: number;
  custoTotal: number;
  valorAtual: number | null;
  precoMedio: number;
  lucroPrejuizo: number;
  rentabilidade: number;
}

export interface Movimentacao {
  id?: number;
  tipo: string;
  data: string;
  quantidade: number;
  precoUnitario: number;
  valorTotal: number;
}

export const investimentoService = {
  listar: async (): Promise<Ativo[]> => {
    const response = await api.get('/investimentos');
    return response.data;
  },

  criar: async (data: Partial<Ativo>): Promise<Ativo> => {
    const response = await api.post('/investimentos', data);
    return response.data;
  },

  atualizar: async (id: number, data: Partial<Ativo>): Promise<Ativo> => {
    const response = await api.put(`/investimentos/${id}`, data);
    return response.data;
  },

  deletar: async (id: number): Promise<void> => {
    await api.delete(`/investimentos/${id}`);
  },

  listarMovimentacoes: async (ativoId: number): Promise<Movimentacao[]> => {
    const response = await api.get(`/investimentos/${ativoId}/movimentacoes`);
    return response.data;
  },

  adicionarMovimentacao: async (ativoId: number, data: Partial<Movimentacao>): Promise<Movimentacao> => {
    const response = await api.post(`/investimentos/${ativoId}/movimentacoes`, data);
    return response.data;
  },
};
