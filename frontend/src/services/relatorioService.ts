import api from './api';

export interface RelatorioCategoriaItem {
  categoriaId: number;
  nome: string;
  cor: string;
  icone: string;
  valorTotal: number;
  porcentagem: number;
}

export interface RelatorioTransacaoItem {
  id: number;
  descricao: string;
  valor: number;
  data: string;
  categoriaNome: string | null;
  categoriaCor: string;
}

export interface RelatorioContaItem {
  contaId: number;
  nome: string;
  tipo: string;
  valorTotal: number;
  porcentagem: number;
}

export interface RelatorioResponse {
  inicio: string;
  fim: string;
  totalEntradas: number;
  totalSaidas: number;
  saldo: number;
  totalTransacoes: number;
  gastosPorCategoria: RelatorioCategoriaItem[];
  maioresDespesas: RelatorioTransacaoItem[];
  gastosPorConta: RelatorioContaItem[];
}

const relatorioService = {
  gerar: async (inicio?: string, fim?: string): Promise<RelatorioResponse> => {
    const params: Record<string, string> = {};
    if (inicio) params.inicio = inicio;
    if (fim) params.fim = fim;
    const response = await api.get<RelatorioResponse>('/relatorios', { params });
    return response.data;
  },
};

export default relatorioService;
