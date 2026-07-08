import api from './api';

export interface CategoriaAlerta {
  categoriaNome: string;
  gastoAtual: number;
  gastoMedio: number;
  variacaoPercentual: number;
  acimaMedia: boolean;
}

export interface InsightsResponse {
  gastoMesAtual: number;
  gastoMedioMensal: number;
  variacaoPercentual: number;
  previsaoSaldoFinal: number;
  categoriasAlerta: CategoriaAlerta[];
  recomendacoes: string[];
  resumo: string;
}

export const insightsService = {
  gerar: async (): Promise<InsightsResponse> => {
    const response = await api.get('/insights');
    return response.data;
  },
};
