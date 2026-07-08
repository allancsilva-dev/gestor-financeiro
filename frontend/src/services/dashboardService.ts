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

export interface ProjecaoMensal {
  periodo: string;
  mes: number;
  ano: number;
  saldoInicial: number;
  totalContasFixas: number;
  totalParcelas: number;
  totalSaidas: number;
  saldoFinal: number;
}

export interface ProjecaoResponse {
  saldoAtual: number;
  meses: ProjecaoMensal[];
}

export interface DashboardResumo {
  totalEntradas: number;
  totalSaidas: number;
  saldo: number;
  totalCategorias: number;
  totalContas: number;
  totalMetas: number;
  totalContasFixas: number;
  saldoCarteiras: number;
}

const dashboardService = {
  resumo: async (signal?: AbortSignal): Promise<DashboardResumo> => {
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
  },

  projecao: async (signal?: AbortSignal): Promise<ProjecaoResponse> => {
    const response = await api.get('/dashboard/projecao', { signal });
    return response.data;
  }
};

export default dashboardService;