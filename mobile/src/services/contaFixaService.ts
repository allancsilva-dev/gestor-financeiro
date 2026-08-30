import api from './api';
import { ContaFixa, ContaFixaRequest, FalhaRecorrencia, PagedResponse, RecorrenciaSugestao } from '../types';

export const contaFixaService = {
  listar: () =>
    api.get<PagedResponse<ContaFixa>>(
      '/v1/contas-fixas/minhas?page=0&size=20&sort=diaVencimento,asc'
    ).then(r => r.data),

  criar: (data: ContaFixaRequest) =>
    api.post<ContaFixa>('/v1/contas-fixas', data).then(r => r.data),

  atualizar: (id: number, data: ContaFixaRequest) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}`, data).then(r => r.data),

  // Debita a carteira informada; assinatura de cartão não tem carteira e entra na fatura
  marcarComoPaga: (id: number, valor: number, carteiraId?: number) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}/realizar`, { valor, carteiraId }).then(r => r.data),

  listarFalhasPendentes: () =>
    api.get<FalhaRecorrencia[]>('/v1/contas-fixas/falhas-pendentes').then(r => r.data),

  pularMes: (id: number) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}/pular`).then(r => r.data),

  reativar: (id: number) =>
    api.put<ContaFixa>(`/v1/contas-fixas/${id}/reativar`).then(r => r.data),

  deletar: (id: number) =>
    api.delete(`/v1/contas-fixas/${id}`),

  /** Padrões de repetição detectados no histórico, aguardando decisão do titular. */
  listarSugestoes: () =>
    api.get<RecorrenciaSugestao[]>('/v1/recorrencias/sugestoes').then(r => r.data),

  confirmarSugestao: (id: number) =>
    api.post<ContaFixa>(`/v1/recorrencias/sugestoes/${id}/confirmar`).then(r => r.data),

  descartarSugestao: (id: number) =>
    api.post<void>(`/v1/recorrencias/sugestoes/${id}/descartar`).then(() => undefined),
};
