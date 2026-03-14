import api from './api';

export interface GastosPorCategoria {
  categoria: string;
  valor: number;
  cor: string;
  percentual: number;
}

export interface EvolucaoMensal {
  mes: string;
  entradas: number;
  saidas: number;
  saldo: number;
}

export interface ResumoComparado {
  periodo: string;
  entradas: number;
  saidas: number;
}

const dashboardService = {
  resumo: async (signal?: AbortSignal): Promise<any> => {
    const response = await api.get('/dashboard/resumo', { signal });
    return response.data;
  },

  // Buscar gastos por categoria do mês atual
  gastosPorCategoria: async (signal?: AbortSignal): Promise<GastosPorCategoria[]> => {
    const response = await api.get('/dashboard/gastos-por-categoria', { signal });
    return response.data;
  },

  // Buscar evolução dos últimos 6 meses
  evolucaoMensal: async (signal?: AbortSignal): Promise<EvolucaoMensal[]> => {
    const response = await api.get('/dashboard/evolucao-mensal', { signal });
    return response.data;
  },

  // Buscar comparação mês atual vs anterior
  comparacaoMensal: async (signal?: AbortSignal): Promise<ResumoComparado[]> => {
    const response = await api.get('/dashboard/comparacao-mensal', { signal });
    return response.data;
  }
};

export default dashboardService;