import api from './api';

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
  listarPorUsuario: async (usuarioId: number) => {
    const response = await api.get(`/contas/usuario/${usuarioId}`);
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