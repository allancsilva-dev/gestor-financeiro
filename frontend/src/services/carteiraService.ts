import api from './api';
import type { PagedResponse } from '../types';

export interface Carteira {
  id: number;
  nome: string;
  tipo: 'DINHEIRO' | 'CONTA_BANCARIA' | 'POUPANCA';
  saldo: number;
  banco?: string;
  usuario: {
    id: number;
  };
}

const carteiraService = {
  // Listar todas as carteiras do usuário
  listarCarteiras: async (_usuarioId: number, page = 0, size = 20): Promise<Carteira[]> => {
    const response = await api.get<PagedResponse<Carteira>>('/carteiras/minhas', {
      params: { page, size },
    });
    return response.data.content ?? [];
  },

  listarCarteirasPaginado: async (page = 0, size = 20): Promise<PagedResponse<Carteira>> => {
    const response = await api.get<PagedResponse<Carteira>>('/carteiras/minhas', {
      params: { page, size },
    });
    return response.data;
  },

  // Buscar carteira por ID
  buscarPorId: async (id: number): Promise<Carteira> => {
    const response = await api.get(`/carteiras/${id}`);
    return response.data;
  },

  // Criar nova carteira
  criar: async (carteira: any): Promise<Carteira> => {
    const response = await api.post('/carteiras', carteira);
    return response.data;
  },

  // Atualizar carteira
  atualizar: async (id: number, carteira: any): Promise<Carteira> => {
    const response = await api.put(`/carteiras/${id}`, carteira);
    return response.data;
  },

  // Deletar carteira
  deletar: async (id: number): Promise<void> => {
    await api.delete(`/carteiras/${id}`);
  },

  // Adicionar dinheiro
  adicionarDinheiro: async (id: number, valor: number): Promise<Carteira> => {
    const response = await api.post(`/carteiras/${id}/adicionar`, { valor });
    return response.data;
  },

  // Remover dinheiro
  removerDinheiro: async (id: number, valor: number): Promise<Carteira> => {
    const response = await api.post(`/carteiras/${id}/remover`, { valor });
    return response.data;
  },

  // Calcular saldo total
  calcularSaldoTotal: async (_usuarioId: number): Promise<number> => {
    const response = await api.get('/carteiras/minhas/saldo-total');
    return response.data;
  }
};

export default carteiraService;