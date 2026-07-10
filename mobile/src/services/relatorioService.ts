import api from './api';
import { ComparacaoMensalItem, EvolucaoMensalItem, RelatorioResponse } from '../types';

const relatorioService = {
  gerar: (inicio?: string, fim?: string) => {
    const params: Record<string, string> = {};
    if (inicio) params.inicio = inicio;
    if (fim) params.fim = fim;
    return api.get<RelatorioResponse>('/v1/relatorios', { params }).then((r) => r.data);
  },

  evolucaoMensal: () =>
    api.get<EvolucaoMensalItem[]>('/v1/dashboard/evolucao-mensal').then((r) => r.data),

  comparacaoMensal: () =>
    api.get<ComparacaoMensalItem[]>('/v1/dashboard/comparacao-mensal').then((r) => r.data),
};

export default relatorioService;
