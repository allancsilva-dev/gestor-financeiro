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
  // Buscar gastos por categoria do mês atual
  gastosPorCategoria: async (): Promise<GastosPorCategoria[]> => {
    const response = await api.get('/dashboard/gastos-por-categoria');
    return response.data;
  },

  // Buscar evolução dos últimos 6 meses
  evolucaoMensal: async (): Promise<EvolucaoMensal[]> => {
    const response = await api.get('/dashboard/evolucao-mensal');
    return response.data;
  },

  // Buscar comparação mês atual vs anterior
  comparacaoMensal: async (): Promise<ResumoComparado[]> => {
    const response = await api.get('/dashboard/comparacao-mensal');
    return response.data;
  }
};

export default dashboardService;