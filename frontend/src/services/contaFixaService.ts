import api from './api';
import type { PagedResponse } from '../types';

export interface ContaFixa {
  id: number;
  nome: string;
  valorPlanejado: number;
  valorReal?: number;
  diaVencimento: number;
  dataProximoVencimento: string;
  ativo: boolean;
  recorrente: boolean;
  status: 'PENDENTE' | 'PAGO' | 'ATRASADO';
  observacoes?: string;
  categoria: {
    id: number;
    nome: string;
    cor: string;
  };
  usuario: {
    id: number;
  };
}

const contaFixaService = {
  // Listar contas fixas ativas do usuário
  listarAtivas: async (_usuarioId: number, page = 0, size = 20): Promise<ContaFixa[]> => {
    const response = await api.get<PagedResponse<ContaFixa>>('/contas-fixas/minhas', {
      params: { page, size },
    });
    return response.data.content ?? [];
  },

  listarAtivasPaginado: async (page = 0, size = 20): Promise<PagedResponse<ContaFixa>> => {
    const response = await api.get<PagedResponse<ContaFixa>>('/contas-fixas/minhas', {
      params: { page, size },
    });
    return response.data;
  },

  // Buscar conta fixa por ID
  buscarPorId: async (id: number): Promise<ContaFixa> => {
    const response = await api.get(`/contas-fixas/${id}`);
    return response.data;
  },

  // Criar nova conta fixa
  criar: async (contaFixa: any): Promise<ContaFixa> => {
    const response = await api.post('/contas-fixas', contaFixa);
    return response.data;
  },

  // Atualizar conta fixa
  atualizar: async (id: number, contaFixa: any): Promise<ContaFixa> => {
    const response = await api.put(`/contas-fixas/${id}`, contaFixa);
    return response.data;
  },

  // Deletar conta fixa (desativa)
  deletar: async (id: number): Promise<void> => {
    await api.delete(`/contas-fixas/${id}`);
  },

  // ✅ CORRIGIDO: Envia valorPago no BODY ao invés de query param
  marcarComoPaga: async (id: number, valorPago: number): Promise<ContaFixa> => {
    const response = await api.put(`/contas-fixas/${id}/pagar`, {
      valor: valorPago
    });
    return response.data;
  }
};

export default contaFixaService;