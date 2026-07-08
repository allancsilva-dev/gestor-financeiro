import api from './api';
import { RelatorioResponse } from '../types';

const relatorioService = {
  gerar: (inicio?: string, fim?: string) => {
    const params: Record<string, string> = {};
    if (inicio) params.inicio = inicio;
    if (fim) params.fim = fim;
    return api.get<RelatorioResponse>('/v1/relatorios', { params }).then((r) => r.data);
  },
};

export default relatorioService;
