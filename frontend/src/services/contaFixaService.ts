import api from './api';

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
  listarAtivas: async (usuarioId: number): Promise<ContaFixa[]> => {
    const response = await api.get(`/contas-fixas/usuario/${usuarioId}`);
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

  // Marcar como paga
  marcarComoPaga: async (id: number, valorPago: number): Promise<ContaFixa> => {
    const response = await api.put(`/contas-fixas/${id}/pagar`, null, {
      params: { valorPago }
    });
    return response.data;
  }
};

export default contaFixaService;  