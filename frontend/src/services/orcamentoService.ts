import api from './api';

export interface OrcamentoCategoriaItem {
  id: number;
  categoriaId: number;
  categoriaNome: string;
  categoriaCor: string;
  categoriaIcone: string;
  valorLimite: number;
  valorGasto: number;
  percentualGasto: number;
}

export interface OrcamentoResponse {
  id: number;
  mes: number;
  ano: number;
  valorTotalPlanejado: number;
  valorTotalGasto: number;
  categorias: OrcamentoCategoriaItem[];
}

export interface OrcamentoRequest {
  mes: number;
  ano: number;
  categorias: { categoriaId: number; valorLimite: number }[];
}

const orcamentoService = {
  buscarAtual: async (): Promise<OrcamentoResponse> => {
    const response = await api.get<OrcamentoResponse>('/orcamentos/atual');
    return response.data;
  },

  buscarPorMes: async (mes: number, ano: number): Promise<OrcamentoResponse> => {
    const response = await api.get<OrcamentoResponse>('/orcamentos', { params: { mes, ano } });
    return response.data;
  },

  criarOuAtualizar: async (data: OrcamentoRequest): Promise<OrcamentoResponse> => {
    const response = await api.post<OrcamentoResponse>('/orcamentos', data);
    return response.data;
  },
};

export default orcamentoService;
