import api from './api';
import { OrcamentoResponse, PoliticaRollover } from '../types';

export interface OrcamentoRequest {
  mes: number;
  ano: number;
  categorias: { categoriaId: number; valorLimite: number; politicaRollover?: PoliticaRollover }[];
}

export const orcamentoService = {
  buscarAtual: () => api.get<OrcamentoResponse>('/v1/orcamentos/atual').then((r) => r.data),

  buscarPorMes: (mes: number, ano: number) =>
    api.get<OrcamentoResponse>('/v1/orcamentos', { params: { mes, ano } }).then((r) => r.data),

  criarOuAtualizar: (data: OrcamentoRequest) =>
    api.post<OrcamentoResponse>('/v1/orcamentos', data).then((r) => r.data),
};
